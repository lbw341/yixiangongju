package com.toolplatform.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolplatform.service.ScriptRunnerService;
import com.toolplatform.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 沙箱执行编排。
 * 把一次"脚本运行"封装进一次性 Linux 容器全限制执行：
 * 准备 input/result/script 容器工作目录 → 绑定挂载（input 只读、result 可写、script 只读）
 * → 把宿主路径映射为容器内挂载点 → 在容器内重建统一 I/O env（DATA_DIR/INPUT_FILES/RESULT_DIR）
 * → 组装 docker run <全限制> <image> <launcher> <runtime> <容器内脚本路径> <dataDir> [files...]
 * → 委托 DockerRunner，时长为 sandbox.timeout。
 *
 * 说明：工具作者的脚本用宿主 `ToolRunner.buildCommand` 产生的解释器路径（如 Windows bash）
 * 在容器内并不存在，故本服务不直接执行 buildCommand 产物；而是交给镜像内 launcher（Task 7）
 * 按 runtime + 脚本 + data目录 + 文件分发到 bash/python3/node/java。统一 I/O env 以容器形式
 * 通过 `-e` 注入，launcher 亦可依此重建。
 */
@Service
public class SandboxExecutionService {

    private static final String CONTAINER_DATA_DIR = "/data";
    private static final String CONTAINER_RESULT_DIR = "/result";
    private static final String CONTAINER_SCRIPT_DIR = "/script";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DockerRunner dockerRunner;
    private final boolean enabled;
    private final String memory;
    private final String cpus;
    private final long timeoutSeconds;
    private final String dockerImage;
    private final String runAsUser;
    private final String launcherContainerPath;

    public SandboxExecutionService(DockerRunner dockerRunner, boolean enabled, String memory,
                                   String cpus, long timeoutSeconds, String dockerImage,
                                   String runAsUser, String launcherContainerPath) {
        this.dockerRunner = dockerRunner;
        this.enabled = enabled;
        this.memory = memory;
        this.cpus = cpus;
        this.timeoutSeconds = timeoutSeconds;
        this.dockerImage = dockerImage;
        this.runAsUser = runAsUser;
        this.launcherContainerPath = launcherContainerPath;
    }

    @Autowired
    public SandboxExecutionService(
            @Value("${sandbox.enabled:true}") boolean enabled,
            @Value("${sandbox.memory:512m}") String memory,
            @Value("${sandbox.cpus:1}") String cpus,
            @Value("${sandbox.timeout:120}") long timeoutSeconds,
            @Value("${sandbox.docker-image:}") String dockerImage,
            @Value("${sandbox.run-as-user:1000:1000}") String runAsUser,
            @Value("${sandbox.launcher-container-path:/usr/local/bin/launcher}") String launcherContainerPath) {
        this(new DockerRunner(), enabled, memory, cpus, timeoutSeconds, dockerImage,
                runAsUser, launcherContainerPath);
    }

    /**
     * 在 Docker 全限制沙箱中运行指定 runtime 的脚本并捕获输出。
     * 结果形态与 ScriptRunnerService.ScriptRunResult 对齐（复用之）。
     */
    public ScriptRunnerService.ScriptRunResult run(String runtime, Path scriptFile, Map<String, byte[]> dataFiles)
            throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("sandbox_");
        try {
            Path dataDir = workDir.resolve("data");
            Files.createDirectories(dataDir);
            List<Path> hostFilePaths = writeDataFiles(dataDir, dataFiles);

            Path resultDir = workDir.resolve("result");
            Files.createDirectories(resultDir);

            Path scriptDir = workDir.resolve("script");
            Files.createDirectories(scriptDir);
            String scriptName = scriptFile.getFileName().toString();
            Files.copy(scriptFile, scriptDir.resolve(scriptName), StandardCopyOption.REPLACE_EXISTING);

            List<String> args = buildDockerRunArgs(dataDir, resultDir, scriptDir, scriptName, runtime, hostFilePaths);

            DockerRunner.Result dockerResult = dockerRunner.run(args, workDir, resultDir, runtime, timeoutSeconds);
            if (dockerResult.isTimedOut()) {
                return ScriptRunnerService.ScriptRunResult.timedOut(dockerResult.getOutput(), runtime);
            }
            return ScriptRunnerService.ScriptRunResult.of(dockerResult.getExitCode(), dockerResult.getOutput(), runtime);
        } finally {
            deleteRecursive(workDir);
        }
    }

    private List<String> buildDockerRunArgs(Path dataDir, Path resultDir, Path scriptDir, String scriptName,
                                            String runtime, List<Path> hostFilePaths) throws IOException {
        List<String> containerFilePaths = new ArrayList<>();
        for (Path p : hostFilePaths) {
            containerFilePaths.add(CONTAINER_DATA_DIR + "/" + dataDir.relativize(p).toString().replace('\\', '/'));
        }

        String inputFilesJson = OBJECT_MAPPER.writeValueAsString(containerFilePaths);

        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("--network");
        args.add("none");
        args.add("--memory");
        args.add(memory);
        args.add("--cpus");
        args.add(cpus);
        args.add("--user");
        args.add(runAsUser);
        args.add("--rm");
        args.add("--mount");
        args.add("type=bind,source=" + dataDir.toAbsolutePath().toString().replace('\\', '/') + ",target=" + CONTAINER_DATA_DIR + ",readonly");
        args.add("--mount");
        args.add("type=bind,source=" + resultDir.toAbsolutePath().toString().replace('\\', '/') + ",target=" + CONTAINER_RESULT_DIR);
        args.add("--mount");
        args.add("type=bind,source=" + scriptDir.toAbsolutePath().toString().replace('\\', '/') + ",target=" + CONTAINER_SCRIPT_DIR + ",readonly");

        args.add("-e");
        args.add("DATA_DIR=" + CONTAINER_DATA_DIR);
        args.add("-e");
        args.add("INPUT_FILES=" + inputFilesJson);
        args.add("-e");
        args.add("RESULT_DIR=" + CONTAINER_RESULT_DIR);

        args.add(dockerImage);
        args.add(launcherContainerPath);
        args.add(runtime);
        args.add(CONTAINER_SCRIPT_DIR + "/" + scriptName);
        args.add(CONTAINER_DATA_DIR);
        args.addAll(containerFilePaths);

        return args;
    }

    /** 把数据文件写入 dataDir 并返回其绝对路径列表（净化文件名，防目录穿越）。 */
    private static List<Path> writeDataFiles(Path dataDir, Map<String, byte[]> dataFiles) throws IOException {
        List<Path> dataFilePaths = new ArrayList<>();
        if (dataFiles != null) {
            for (Map.Entry<String, byte[]> entry : dataFiles.entrySet()) {
                Path dataFile = PathUtils.resolveDataPath(dataDir, entry.getKey());
                Files.createDirectories(dataFile.getParent());
                Files.write(dataFile, entry.getValue());
                dataFilePaths.add(dataFile.toAbsolutePath());
            }
        }
        return dataFilePaths;
    }

    private static void deleteRecursive(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var entries = Files.list(path)) {
                    for (Path entry : (Iterable<Path>) entries::iterator) {
                        deleteRecursive(entry);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
