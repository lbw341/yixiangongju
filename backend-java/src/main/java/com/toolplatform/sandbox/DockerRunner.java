package com.toolplatform.sandbox;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DockerRunner {

    private static final int MAX_OUTPUT_LINES = 5000;
    private static final long MAX_OUTPUT_CHARS = 1_000_000;

    private final String dockerCommand;

    public DockerRunner() {
        this("docker");
    }

    public DockerRunner(String dockerCommand) {
        this.dockerCommand = dockerCommand;
    }

    public Result run(List<String> containerFullCommand, Path workDir, Path resultDir, String runtime, long timeoutSeconds)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(dockerCommand);
        command.addAll(containerFullCommand);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }

        Process process = pb.start();

        StringBuffer output = new StringBuffer();
        Thread readerThread = startOutputReader(process.getInputStream(), output);

        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
        }
        readerThread.join(2000);

        if (!completed) {
            return Result.timedOut(output.toString());
        }
        return Result.of(process.exitValue(), output.toString());
    }

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
                // process destroyed forcibly — expected
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    public static class Result {
        private final int exitCode;
        private final String output;
        private final boolean timedOut;

        private Result(int exitCode, String output, boolean timedOut) {
            this.exitCode = exitCode;
            this.output = output;
            this.timedOut = timedOut;
        }

        public static Result of(int exitCode, String output) {
            return new Result(exitCode, output, false);
        }

        public static Result timedOut(String output) {
            return new Result(-1, output, true);
        }

        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public boolean isTimedOut() { return timedOut; }
    }
}
