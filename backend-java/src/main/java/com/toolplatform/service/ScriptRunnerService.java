package com.toolplatform.service;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 脚本执行服务
 * 只负责"运行一个脚本并收回结果"，不感知工具/模板业务。
 * 约定: <python> -u <script> <dataDir> [file1] [file2] ...
 * sys.argv[1] 恒为数据目录，sys.argv[2:] 为数据文件列表（可为空）。
 */
@Service
public class ScriptRunnerService {

    private static final int MAX_OUTPUT_LINES = 5000;
    private static final long MAX_OUTPUT_CHARS = 1_000_000;
    private static final int TIMEOUT_SECONDS = 120;

    private final Object pythonLookupLock = new Object();
    private volatile boolean pythonLookupDone;
    private volatile String cachedPythonCmd;

    /**
     * 运行脚本并捕获输出。数据文件写入临时目录后以绝对路径传给脚本。
     * 无论成功失败，临时工作目录都会被清理。
     */
    public ScriptRunResult run(Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("tool_script_");
        try {
            Path dataDir = workDir.resolve("data");
            Files.createDirectories(dataDir);
            List<String> dataFilePaths = new ArrayList<>();
            if (dataFiles != null) {
                for (Map.Entry<String, byte[]> entry : dataFiles.entrySet()) {
                    Path dataFile = dataDir.resolve(sanitizeFileName(entry.getKey()));
                    Files.write(dataFile, entry.getValue());
                    dataFilePaths.add(dataFile.toAbsolutePath().toString());
                }
            }

            String pythonCmd = findPythonCommand();
            if (pythonCmd == null) {
                return ScriptRunResult.pythonMissing();
            }

            List<String> command = new ArrayList<>();
            command.add(pythonCmd);
            command.add("-u");
            command.add(scriptFile.toAbsolutePath().toString());
            command.add(dataDir.toAbsolutePath().toString());
            command.addAll(dataFilePaths);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(workDir.toFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUTF8", "1");

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Thread readerThread = startOutputReader(process.getInputStream(), output);

            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            readerThread.join(2000);

            if (!completed) {
                return ScriptRunResult.timedOut(output.toString());
            }
            return ScriptRunResult.of(process.exitValue(), output.toString());
        } finally {
            deleteDirectory(workDir.toFile());
        }
    }

    /**
     * 剥离路径成分并替换非法字符，拦截空名/./..
     */
    private String sanitizeFileName(String originalName) {
        int lastSlash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        String fileName = lastSlash >= 0 ? originalName.substring(lastSlash + 1) : originalName;
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
            fileName = "file_" + Math.abs(originalName.hashCode());
        }
        return fileName;
    }

    /**
     * Python 解释器探测，进程内只探测一次（含否定结果缓存）
     */
    private String findPythonCommand() {
        if (pythonLookupDone) {
            return cachedPythonCmd;
        }
        synchronized (pythonLookupLock) {
            if (!pythonLookupDone) {
                cachedPythonCmd = detectPythonCommand();
                pythonLookupDone = true;
            }
        }
        return cachedPythonCmd;
    }

    private String detectPythonCommand() {
        String[] candidates = {"python", "python3", "py"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                Process p = pb.start();
                boolean ok = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
                p.destroyForcibly();
                if (ok) {
                    return cmd;
                }
            } catch (Exception e) {
                // 探测失败继续尝试下一个候选
            }
        }

        String[] paths = {
                "C:\\Python39\\python.exe",
                "C:\\Python310\\python.exe",
                "C:\\Python311\\python.exe",
                "C:\\Python312\\python.exe",
                "C:\\Program Files\\Python39\\python.exe",
                "C:\\Program Files\\Python310\\python.exe",
                "C:\\Program Files\\Python311\\python.exe",
                "C:\\Program Files\\Python312\\python.exe",
                "C:\\Program Files (x86)\\Python39\\python.exe",
                "C:\\Program Files (x86)\\Python310\\python.exe",
                "C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python311\\python.exe",
                "C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python312\\python.exe"
        };
        for (String path : paths) {
            if (new File(path).exists()) {
                return path;
            }
        }

        return null;
    }

    /**
     * 后台线程读取合并输出，超限截断。设为守护线程避免阻塞 JVM 退出。
     */
    private Thread startOutputReader(InputStream stream, StringBuilder output) {
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

        private ScriptRunResult(boolean pythonFound, boolean timedOut, int exitCode, String output) {
            this.pythonFound = pythonFound;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
        }

        public static ScriptRunResult pythonMissing() {
            return new ScriptRunResult(false, false, -1, "");
        }

        public static ScriptRunResult timedOut(String output) {
            return new ScriptRunResult(true, true, -1, output);
        }

        public static ScriptRunResult of(int exitCode, String output) {
            return new ScriptRunResult(true, false, exitCode, output);
        }

        public boolean isPythonFound() { return pythonFound; }
        public boolean isTimedOut() { return timedOut; }
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
    }
}
