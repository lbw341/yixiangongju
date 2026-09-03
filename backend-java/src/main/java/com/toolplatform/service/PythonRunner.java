package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PythonRunner implements ToolRunner {

    @Override
    public String runtimeType() { return "python"; }

    @Override
    public List<String> buildCommand(String script, Path dataDir, List<String> filePaths) {
        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add("-u");
        cmd.add(script);
        cmd.add(dataDir.toAbsolutePath().toString());
        cmd.addAll(filePaths);
        return cmd;
    }

    @Override
    public boolean supportsEntry(String file) {
        return file != null && file.toLowerCase().endsWith(".py");
    }

    @Override
    public String resolveCommand() { return "python"; }
}
