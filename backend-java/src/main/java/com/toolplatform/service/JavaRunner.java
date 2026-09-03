package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class JavaRunner implements ToolRunner {
    private final CommandResolver resolver;

    public JavaRunner(CommandResolver resolver) { this.resolver = resolver; }

    @Override
    public String runtimeType() { return "java"; }

    @Override
    public List<String> buildCommand(String script, Path dataDir, List<String> filePaths) {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(script);
        cmd.add(dataDir.toAbsolutePath().toString());
        cmd.addAll(filePaths);
        return cmd;
    }

    @Override
    public boolean supportsEntry(String file) {
        return file != null && file.toLowerCase().endsWith(".jar");
    }

    @Override
    public String resolveCommand() { return resolver.isJavaPresent() ? "java" : null; }
}
