package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class NodeRunner implements ToolRunner {
    private final CommandResolver resolver;

    public NodeRunner(CommandResolver resolver) { this.resolver = resolver; }

    @Override
    public String runtimeType() { return "node"; }

    @Override
    public List<String> buildCommand(String script, Path dataDir, List<String> filePaths) {
        List<String> cmd = new ArrayList<>();
        cmd.add(resolver.resolveNode());
        cmd.add(script);
        cmd.add(dataDir.toAbsolutePath().toString());
        cmd.addAll(filePaths);
        return cmd;
    }

    @Override
    public boolean supportsEntry(String file) {
        if (file == null) return false;
        String f = file.toLowerCase();
        return f.endsWith(".js") || f.endsWith(".mjs");
    }

    @Override
    public String resolveCommand() { return resolver.resolveNode(); }
}
