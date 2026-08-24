package com.toolplatform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 工具脚本包服务
 * 把一次上传落成合法的脚本包目录与可用解释器环境（zip 或平铺两种模式）。
 * 目录布局: uploads/script_packages/<toolId>/payload/** 与 original.zip；uploads/venvs/<toolId>
 */
@Service
public class ScriptPackageService {

    /** 平铺/压缩包成员一律禁止的可执行扩展名 */
    private static final Set<String> BLOCKED_EXT = Set.of("exe", "dll", "bat", "cmd", "ps1", "msi", "scr", "com", "jar");
    private static final String[] ENTRY_CANDIDATES = {"main.py", "app.py", "run.py"};
    private static final String REQUIREMENTS_NAME = "requirements.txt";
    private static final int MAX_PACKAGE_ENTRIES = 500;
    private static final long MAX_PACKAGE_BYTES = 200L * 1024 * 1024;
    private static final int PIP_TIMEOUT_SECONDS = 300;
    private static final int VENV_TIMEOUT_SECONDS = 120;
    private static final int LOG_TAIL_CHARS = 2000;

    @Value("${upload.script-package-dir}")
    private String scriptPackageDir;

    @Value("${upload.venv-dir}")
    private String venvDir;

    private final ScriptRunnerService scriptRunner;

    public ScriptPackageService(ScriptRunnerService scriptRunner) {
        this.scriptRunner = scriptRunner;
    }

    /** 安装失败异常，message 即用户可见文案 */
    public static class PackageInstallException extends IOException {
        public PackageInstallException(String message) { super(message); }
    }

    /** 安装结果 */
    public static class PackageInstallResult {
        private final String packageDir;
        private final String entryFile;
        private final String displayName;

        public PackageInstallResult(String packageDir, String entryFile, String displayName) {
            this.packageDir = packageDir;
            this.entryFile = entryFile;
            this.displayName = displayName;
        }

        public String getPackageDir() { return packageDir; }
        public String getEntryFile() { return entryFile; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 整包替换式安装：先清理旧产物，落盘、校验、探测入口、按需装依赖。
     * 任一步失败则清理半成品并抛出 PackageInstallException。
     */
    public PackageInstallResult install(Long toolId, List<MultipartFile> files) throws IOException, InterruptedException {
        boolean zipMode = files.size() == 1 && files.get(0).getOriginalFilename() != null
                && files.get(0).getOriginalFilename().toLowerCase().endsWith(".zip");
        if (!zipMode) {
            for (MultipartFile f : files) {
                String n = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
                if (n.toLowerCase().endsWith(".zip")) {
                    throw new PackageInstallException("zip 包必须单独上传，不能与其他文件同时选择");
                }
            }
        }

        Path pkgRoot = Paths.get(scriptPackageDir, String.valueOf(toolId));
        Path payload = pkgRoot.resolve("payload");
        deleteArtifacts(toolId);
        Files.createDirectories(payload);

        try {
            String displayName;
            if (zipMode) {
                MultipartFile zip = files.get(0);
                String originalName = zip.getOriginalFilename() == null ? "script.zip" : baseName(zip.getOriginalFilename());
                Files.copy(zip.getInputStream(), pkgRoot.resolve("original.zip"), StandardCopyOption.REPLACE_EXISTING);
                extractZipSafe(pkgRoot.resolve("original.zip"), payload);
                displayName = originalName;
            } else {
                Set<String> seen = new HashSet<>();
                for (MultipartFile f : files) {
                    String name = baseName(f.getOriginalFilename() == null ? "" : f.getOriginalFilename());
                    checkBlocked(name);
                    if (!seen.add(name.toLowerCase())) {
                        throw new PackageInstallException("存在重名文件: " + name);
                    }
                    Files.copy(f.getInputStream(), payload.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
                displayName = firstPyIn(payload);
            }

            String entryFile = resolveEntry(payload);
            if (entryFile == null) {
                throw new PackageInstallException("无法确定入口脚本：请在包根目录提供 main.py / app.py / run.py，或确保只有一个根级 .py 文件");
            }

            if (Files.exists(payload.resolve(REQUIREMENTS_NAME))) {
                setupVenv(toolId, payload.resolve(REQUIREMENTS_NAME));
            }

            return new PackageInstallResult(String.valueOf(toolId), entryFile, displayName);
        } catch (IOException | InterruptedException e) {
            deleteArtifacts(toolId);
            throw e;
        }
    }

    public Path resolvePayload(Long toolId) {
        return Paths.get(scriptPackageDir, String.valueOf(toolId), "payload");
    }

    public Path resolveOriginalZip(Long toolId) {
        return Paths.get(scriptPackageDir, String.valueOf(toolId), "original.zip");
    }

    public Path resolveVenvPython(Long toolId) {
        Path win = Paths.get(venvDir, String.valueOf(toolId), "Scripts", "python.exe");
        if (Files.exists(win)) return win;
        return Paths.get(venvDir, String.valueOf(toolId), "bin", "python");
    }

    /** 删除该工具的脚本包目录与 venv（幂等） */
    public void deleteArtifacts(Long toolId) {
        deleteRecursively(Paths.get(scriptPackageDir, String.valueOf(toolId)).toFile());
        deleteRecursively(Paths.get(venvDir, String.valueOf(toolId)).toFile());
    }

    /** 将 payload 现场打包为 zip 写入输出流（平铺模式下载用） */
    public void zipPayloadTo(Long toolId, OutputStream out) throws IOException {
        Path payload = resolvePayload(toolId);
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            Files.walk(payload).filter(Files::isRegularFile).forEach(p -> {
                try {
                    zos.putNextEntry(new ZipEntry(payload.relativize(p).toString().replace('\\', '/')));
                    Files.copy(p, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private void extractZipSafe(Path zipFile, Path payload) throws IOException {
        Exception last = null;
        for (Charset cs : List.of(StandardCharsets.UTF_8, Charset.forName("GB18030"))) {
            try {
                doExtract(zipFile, payload, cs);
                return;
            } catch (PackageInstallException e) {
                throw (PackageInstallException) e;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new PackageInstallException("无法解析 zip 包: " + (last != null ? last.getMessage() : "未知错误"));
    }

    private void doExtract(Path zipFile, Path payload, Charset cs) throws IOException {
        int count = 0;
        long total = 0;
        try (ZipFile zf = new ZipFile(zipFile.toFile(), cs)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                count++;
                if (count > MAX_PACKAGE_ENTRIES) throw new PackageInstallException("包内文件数超过上限 (" + MAX_PACKAGE_ENTRIES + ")");
                Path target = safeResolve(payload, e.getName());
                checkBlocked(target.getFileName().toString());
                total += Math.max(e.getSize(), 0);
                if (total > MAX_PACKAGE_BYTES) throw new PackageInstallException("解压后总大小超过上限 (200MB)");
                Files.createDirectories(target.getParent());
                try (InputStream is = zf.getInputStream(e)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path safeResolve(Path payload, String entryName) throws PackageInstallException {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new PackageInstallException("非法的压缩包路径: " + entryName);
        }
        Path target = payload.normalize().resolve(normalized).normalize();
        if (!target.startsWith(payload.normalize())) {
            throw new PackageInstallException("非法的压缩包路径: " + entryName);
        }
        return target;
    }

    private String resolveEntry(Path payload) throws IOException {
        for (String cand : ENTRY_CANDIDATES) {
            if (Files.isRegularFile(payload.resolve(cand))) return cand;
        }
        List<String> roots = new ArrayList<>();
        try (var stream = Files.list(payload)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString();
                if (Files.isRegularFile(p) && name.toLowerCase().endsWith(".py")) roots.add(name);
            }
        }
        return roots.size() == 1 ? roots.get(0) : null;
    }

    private void setupVenv(Long toolId, Path requirements) throws IOException, InterruptedException {
        String python = scriptRunner.findPythonCommand();
        if (python == null) {
            throw new PackageInstallException("错误: 服务端未安装Python或Python未添加到环境变量");
        }
        Path venvPath = Paths.get(venvDir, String.valueOf(toolId));
        RunResult venvRes = runCapture(python, new String[]{"-m", "venv", venvPath.toAbsolutePath().toString()}, VENV_TIMEOUT_SECONDS);
        if (!venvRes.completed || venvRes.exitCode != 0) {
            throw new PackageInstallException("虚拟环境创建失败\n" + tail(venvRes.output));
        }
        Path venvPy = resolveVenvPython(toolId);
        RunResult pipRes = runCapture(venvPy.toAbsolutePath().toString(),
                new String[]{"-m", "pip", "install", "-r", requirements.toAbsolutePath().toString(), "--disable-pip-version-check"},
                PIP_TIMEOUT_SECONDS);
        if (!pipRes.completed) {
            throw new PackageInstallException("依赖安装超时 (" + PIP_TIMEOUT_SECONDS + "秒)\n" + tail(pipRes.output));
        }
        if (pipRes.exitCode != 0) {
            throw new PackageInstallException("依赖安装失败，请检查 requirements.txt\n" + tail(pipRes.output));
        }
    }

    private static class RunResult {
        final boolean completed;
        final int exitCode;
        final String output;
        RunResult(boolean completed, int exitCode, String output) {
            this.completed = completed;
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private RunResult runCapture(String cmd, String[] args, int timeoutSeconds) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(cmd);
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (var reader2 = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader2.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        reader.join(2000);
        return new RunResult(completed, completed ? process.exitValue() : -1, output.toString());
    }

    private void checkBlocked(String fileName) throws PackageInstallException {
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        if (BLOCKED_EXT.contains(ext)) {
            throw new PackageInstallException("不允许的可执行文件: " + fileName);
        }
    }

    private String firstPyIn(Path payload) throws IOException {
        try (var stream = Files.list(payload)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString();
                if (Files.isRegularFile(p) && name.toLowerCase().endsWith(".py")) return name;
            }
        }
        return "script";
    }

    private String baseName(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String tail(String s) {
        if (s == null) return "";
        return s.length() <= LOG_TAIL_CHARS ? s : s.substring(s.length() - LOG_TAIL_CHARS);
    }

    private void deleteRecursively(java.io.File dir) {
        java.io.File[] children = dir.listFiles();
        if (children != null) {
            for (java.io.File child : children) deleteRecursively(child);
        }
        dir.delete();
    }
}
