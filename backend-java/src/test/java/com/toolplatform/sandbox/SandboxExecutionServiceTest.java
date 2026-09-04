package com.toolplatform.sandbox;

import com.toolplatform.service.ScriptRunnerService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SandboxExecutionServiceTest {

    @Test
    void buildsDockerRunArgvWithLimitsMountsEnvAndContainerPaths() throws Exception {
        Path tempDir = Files.createTempDirectory("sandbox_argv_test");
        try {
            Path argsFile = tempDir.resolve("args.log");
            Path dockerShim = createDockerShim(tempDir, argsFile, 0);

            Map<String, byte[]> dataFiles = Map.of(
                    "input.csv", "a,b\n1,2\n".getBytes(),
                    "sub/other.txt", "hello".getBytes()
            );
            Path script = tempDir.resolve("main.py");
            Files.writeString(script, "print('hi')");

            SandboxExecutionService svc = new SandboxExecutionService(
                    new DockerRunner(dockerShim.toAbsolutePath().toString()),
                    true, "512m", "1", 30, "sandbox:latest", "1000:1000",
                    "/usr/local/bin/launcher");

            ScriptRunnerService.ScriptRunResult result = svc.run("python", script, dataFiles);

            assertEquals(0, result.getExitCode(), "output: " + result.getOutput());
            assertFalse(result.isTimedOut());
            assertEquals("python", result.getRuntime());

            List<String> argv = readArgs(argsFile);

            // limit flags
            assertTrue(argv.contains("--network"), argv.toString());
            assertTrue(argv.contains("none"));
            assertTrue(argv.contains("--memory"));
            assertTrue(argv.contains("512m"));
            assertTrue(argv.contains("--cpus"));
            assertTrue(argv.contains("1"));
            assertTrue(argv.contains("--user"));
            assertTrue(argv.contains("1000:1000"));

            // image + launcher contract
            assertTrue(argv.contains("sandbox:latest"));
            assertTrue(argv.contains("/usr/local/bin/launcher"));
            int launcherIdx = argv.indexOf("/usr/local/bin/launcher");
            assertEquals("python", argv.get(launcherIdx + 1), "launcher arg 1 = runtime");
            assertEquals("/script/main.py", argv.get(launcherIdx + 2), "launcher arg 2 = script");
            assertEquals("/data", argv.get(launcherIdx + 3), "launcher arg 3 = dataDir");
            assertTrue(argv.contains("/data/input.csv"));
            assertTrue(argv.contains("/data/sub/other.txt"));

            // bind mounts
            String dataMount = mountFor(argv, "target=/data");
            assertTrue(dataMount.contains("readonly"), "input mount must be read-only: " + dataMount);
            String resultMount = mountFor(argv, "target=/result");
            assertFalse(resultMount.contains("readonly"), "result mount must be writable: " + resultMount);
            String scriptMount = mountFor(argv, "target=/script");
            assertTrue(scriptMount.contains("readonly"), "script mount must be read-only: " + scriptMount);

            // container-form env rebuild
            assertEquals("/data", envValue(argv, "DATA_DIR"));
            assertEquals("/result", envValue(argv, "RESULT_DIR"));
            String inputFiles = envValue(argv, "INPUT_FILES");
            assertNotNull(inputFiles);
            assertTrue(inputFiles.contains("/data/input.csv"), "INPUT_FILES: " + inputFiles);
            assertTrue(inputFiles.contains("/data/sub/other.txt"), "INPUT_FILES: " + inputFiles);
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private static String mountFor(List<String> argv, String target) {
        for (int i = 0; i < argv.size(); i++) {
            if (argv.get(i).equals("--mount") && i + 1 < argv.size() && argv.get(i + 1).contains(target)) {
                return argv.get(i + 1);
            }
        }
        return null;
    }

    private static String envValue(List<String> argv, String key) {
        for (int i = 0; i < argv.size(); i++) {
            if (argv.get(i).equals("-e") && i + 1 < argv.size() && argv.get(i + 1).startsWith(key + "=")) {
                return argv.get(i + 1).substring(key.length() + 1);
            }
        }
        return null;
    }

    private static List<String> readArgs(Path argsFile) throws IOException {
        List<String> lines = Files.readAllLines(argsFile);
        assertEquals(1, lines.size(), "expected single line of args");
        String line = lines.get(0).trim();
        List<String> tokens = new ArrayList<>();
        if (!line.isEmpty()) {
            for (String t : line.split("\\s+")) {
                if (!t.isEmpty()) tokens.add(t);
            }
        }
        return tokens;
    }

    private Path createDockerShim(Path dir, Path argsFile, int exitCode) throws IOException {
        Path shim = dir.resolve("docker.cmd");
        String escaped = argsFile.toAbsolutePath().toString().replace("\\", "\\\\");
        String content = "@echo off\r\n"
                + "echo %* >> \"" + escaped + "\"\r\n"
                + "exit /b " + exitCode + "\r\n";
        Files.writeString(shim, content);
        return shim;
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
