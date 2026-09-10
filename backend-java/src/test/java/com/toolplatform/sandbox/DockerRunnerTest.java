package com.toolplatform.sandbox;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockerRunnerTest {

    @Test
    void runPassesExactArgvToDocker() throws Exception {
        Path tempDir = Files.createTempDirectory("docker_runner_test");
        try {
            Path argsFile = tempDir.resolve("args.log");
            Path dockerShim = createDockerShim(tempDir, argsFile, 0);

            List<String> containerCommand = List.of(
                    "run", "--rm",
                    "--network", "none",
                    "--memory", "512m",
                    "--cpus", "1",
                    "--user", "1000:1000",
                    "--mount", "type=bind,source=/data,target=/app"
            );

            DockerRunner runner = new DockerRunner(dockerShim.toAbsolutePath().toString());
            DockerRunner.Result result = runner.run(containerCommand, tempDir, tempDir, "python", 30);

            assertEquals(0, result.getExitCode(), "exit code: " + result.getOutput());
            assertFalse(result.isTimedOut());

            List<String> logged = Files.readAllLines(argsFile);
            assertEquals(1, logged.size(), "expected single line of args");
            String line = logged.get(0).trim();
            String[] tokens = line.split("\\s+");
            assertEquals(containerCommand.size(), tokens.length, "unexpected arg count");
            for (int i = 0; i < containerCommand.size(); i++) {
                assertEquals(containerCommand.get(i), tokens[i],
                        "arg mismatch at index " + i);
            }
        } finally {
            deleteRecursive(tempDir);
        }
    }

    @Test
    void runTimesOutWhenDockerHangs() throws Exception {
        Path tempDir = Files.createTempDirectory("docker_runner_timeout");
        try {
            Path dockerShim = createSleepingDockerShim(tempDir);

            DockerRunner runner = new DockerRunner(dockerShim.toAbsolutePath().toString());
            DockerRunner.Result result = runner.run(List.of("run", "--rm", "alpine"), tempDir, tempDir, "python", 1);

            assertTrue(result.isTimedOut(), "expected timedOut=true");
            assertEquals(-1, result.getExitCode());
        } finally {
            deleteRecursive(tempDir);
        }
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

    private Path createSleepingDockerShim(Path dir) throws IOException {
        Path shim = dir.resolve("docker.cmd");
        String content = "@echo off\r\n"
                + "ping -n 11 127.0.0.1 > nul\r\n"
                + "exit /b 0\r\n";
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
