package com.toolplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 脚本执行服务
 * 只负责"运行一个脚本并收回结果"，不感知工具/模板业务。
 * 按 runtime 分发到对应的 ToolRunner，并注入统一的 I/O 环境变量。
 * 约定: <interpreter> <script> <dataDir> [file1] [file2] ...
 * sys.argv[1] 恒为数据目录，sys.argv[2:] 为数据文件列表（可为空）。
 * 环境变量: DATA_DIR / INPUT_FILES(JSON数组) / RESULT_DIR。
 */
@Service
public class ScriptRunnerService {

    private static final int MAX_OUTPUT_LINES = 5000;
    private static final long MAX_OUTPUT_CHARS = 1_000_000;
    private static final int TIMEOUT_SECONDS = 120;
    private static final int JAVA_TIMEOUT_SECONDS = 180;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CommandResolver resolver;
    private final Map<String, ToolRunner> runners;

    @Autowired
    public ScriptRunnerService(CommandResolver resolver, List<ToolRunner> runners) {
        this.resolver = resolver;
        Map<String, ToolRunner> m = new HashMap<>();
        if (runners != null) for (ToolRunner r : runners) m.put(r.runtimeType(), r);
        this.runners = m;
    }

    /**
     * 运行脚本并捕获输出（自动按 python runtime）。数据文件写入临时目录后以绝对路径传给脚本。
     * 无论成功失败，临时工作目录都会被清理。
     */
    public ScriptRunResult run(Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        return runBy("python", scriptFile, dataFiles);
    }

    /**
     * 显式指定解释器/runtime（pythonCmd 为 null 时自动按 python runtime 探测）
     */
    public ScriptRunResult run(String pythonCmd, Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        return runBy(pythonCmd == null ? "python" : pythonCmd, scriptFile, dataFiles);
    }

    /**
     * 按 runtime 分发到对应 ToolRunner 并统一注入 I/O 环境变量。
     * 未知 runtime 或对应解释器缺失时返回 runtimeMissing。
     */
    public ScriptRunResult runBy(String runtime, Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("tool_script_");
        try {
            Path dataDir = workDir.resolve("data");
            Files.createDirectories(dataDir);
            List<String> dataFilePaths = new ArrayList<>();
            if (dataFiles != null) {
                for (Map.Entry<String, byte[]> entry : dataFiles.entrySet()) {
                    Path dataFile = resolveDataPath(dataDir, entry.getKey());
                    Files.createDirectories(dataFile.getParent());
                    Files.write(dataFile, entry.getValue());
                    dataFilePaths.add(dataFile.toAbsolutePath().toString());
                }
            }

            ToolRunner runner = runners.get(runtime);
            if (runner == null) {
                return ScriptRunResult.runtimeMissing(runtime);
            }

            List<String> command = runner.buildCommand(scriptFile.toAbsolutePath().toString(), dataDir, dataFilePaths);
            if (command == null || command.isEmpty() || command.get(0) == null) {
                return ScriptRunResult.runtimeMissing(runtime);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(workDir.toFile());

            Path resultDir = workDir.resolve("result");
            Files.createDirectories(resultDir);
            pb.environment().put("DATA_DIR", dataDir.toAbsolutePath().toString());
            pb.environment().put("INPUT_FILES", OBJECT_MAPPER.writeValueAsString(dataFilePaths));
            pb.environment().put("RESULT_DIR", resultDir.toAbsolutePath().toString());

            if ("python".equals(runtime)) {
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                pb.environment().put("PYTHONUTF8", "1");
            }

            Process process = pb.start();

            StringBuffer output = new StringBuffer();
            Thread readerThread = startOutputReader(process.getInputStream(), output);

            int timeout = "java".equals(runtime) ? JAVA_TIMEOUT_SECONDS : TIMEOUT_SECONDS;
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            readerThread.join(2000);

            if (!completed) {
                return ScriptRunResult.timedOut(output.toString(), runtime);
            }
            return ScriptRunResult.of(process.exitValue(), output.toString(), runtime);
        } finally {
            deleteDirectory(workDir.toFile());
        }
    }

    /**
     * 剥离路径成分并替换非法字符，拦截空名/./..
     */
    private static String sanitizeFileName(String originalName) {
        int lastSlash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        String fileName = lastSlash >= 0 ? originalName.substring(lastSlash + 1) : originalName;
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
            fileName = "file_" + Math.abs(originalName.hashCode());
        }
        return fileName;
    }

    /**
     * 将上传的数据文件键名（可能含子目录相对路径）安全解析为 dataDir 下的写入路径。
     * 逐组件复用 sanitizeFileName 净化；对绝对路径 / 盘符 / ".." 做穿越防护，
     * 转换则退回按最外层文件名摊平，保证写入始终落在 dataDir 内。
     */
    static Path resolveDataPath(Path dataDir, String name) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || (normalized.length() >= 2 && normalized.charAt(1) == ':')) {
            return dataDir.resolve(sanitizeFileName(name));
        }
        Path current = dataDir;
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                return dataDir.resolve(sanitizeFileName(basename(normalized)));
            }
            current = current.resolve(sanitizeFileName(part));
        }
        if (!current.normalize().startsWith(dataDir.normalize())
                || current.normalize().equals(dataDir.normalize())) {
            return dataDir.resolve(sanitizeFileName(basename(normalized)));
        }
        return current;
    }

    private static String basename(String normalized) {
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    /**
     * Python 解释器探测（委托 resolver 缓存），供脚本包依赖安装复用
     */
    public String findPythonCommand() {
        return resolver.isPythonPresent() ? "python" : null;
    }

    /**
     * 后台线程读取合并输出，超限截断。设为守护线程避免阻塞 JVM 退出。
     */
    private Thread startOutputReader(InputStream stream, StringBuffer output) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                int lineCount = 0;
                long charCount = 0;
                boolean linesTruncated = false;
                boolean charsTruncated = false;
                while ((line = reader.readLine()) != null) {
                    if (lineCount < MAX_OUTPUT_LINES && charCount < MAX_OUTPUT_CHARS) {
                        output.append(line).append('\n');
                        lineCount++;
                        charCount += line.length();
                    } else if (lineCount >= MAX_OUTPUT_LINES) {
                        linesTruncated = true;
                    } else {
                        charsTruncated = true;
                    }
                }
                if (linesTruncated) {
                    output.append("\n[输出行数过多，已截断]\n");
                } else if (charsTruncated) {
                    output.append("\n[输出内容过大，已截断]\n");
                }
            } catch (IOException e) {
                // 进程被强杀时流会异常关闭，属预期情况
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void deleteDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteDirectory(child);
            }
        }
        dir.delete();
    }

    /**
     * 脚本运行结果
     */
    public static class ScriptRunResult {
        private final boolean pythonFound;
        private final boolean timedOut;
        private final int exitCode;
        private final String output;
        private final String runtime;

        private ScriptRunResult(boolean pythonFound, boolean timedOut, int exitCode, String output, String runtime) {
            this.pythonFound = pythonFound;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
            this.runtime = runtime;
        }

        public static ScriptRunResult pythonMissing() {
            return new ScriptRunResult(false, false, -1, "", "python");
        }

        public static ScriptRunResult runtimeMissing(String runtime) {
            return new ScriptRunResult(false, false, -1, "", runtime);
        }

        public static ScriptRunResult timedOut(String output) {
            return new ScriptRunResult(true, true, -1, output, "python");
        }

        public static ScriptRunResult timedOut(String output, String runtime) {
            return new ScriptRunResult(true, true, -1, output, runtime);
        }

        public static ScriptRunResult of(int exitCode, String output) {
            return new ScriptRunResult(true, false, exitCode, output, "python");
        }

        public static ScriptRunResult of(int exitCode, String output, String runtime) {
            return new ScriptRunResult(true, false, exitCode, output, runtime);
        }

        public boolean isPythonFound() { return pythonFound; }
        public boolean isTimedOut() { return timedOut; }
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public String getRuntime() { return runtime; }
    }
}
