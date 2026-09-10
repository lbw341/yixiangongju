package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** BashRunner — 支持 .sh 脚本，Windows 上优先用 Git Bash */
@Component
public class BashRunner implements ToolRunner {
    private final CommandResolver resolver;

    public BashRunner(CommandResolver resolver) { this.resolver = resolver; }

    @Override
    public String runtimeType() { return "bash"; }

    @Override
    public List<String> buildCommand(String script, Path dataDir, List<String> filePaths) {
        String bash = resolver.resolveBash();
        List<String> cmd = new ArrayList<>();
        cmd.add(bash);  // 绝对路径或 "bash"（detect 过的）
        cmd.add(script);
        cmd.add(dataDir.toAbsolutePath().toString());
        cmd.addAll(filePaths);
        return cmd;
    }

    @Override
    public boolean supportsEntry(String file) {
        if (file == null) return false;
        String f = file.toLowerCase();
        return f.endsWith(".sh") || f.endsWith(".bash");
    }

    @Override
    public String resolveCommand() { return resolver.resolveBash(); }
}
