package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** BatRunner — Windows Batch 脚本（.bat / .cmd） */
@Component
public class BatRunner implements ToolRunner {
    private final CommandResolver resolver;

    public BatRunner(CommandResolver resolver) { this.resolver = resolver; }

    @Override
    public String runtimeType() { return "bat"; }

    @Override
    public List<String> buildCommand(String script, Path dataDir, List<String> filePaths) {
        String cmdExe = resolver.resolveCmd();
        // cmd /c script arg1 arg2 ...
        List<String> cmd = new ArrayList<>();
        cmd.add(cmdExe);   // C:\Windows\System32\cmd.exe
        cmd.add("/c");
        cmd.add(script);
        cmd.add(dataDir.toAbsolutePath().toString());
        cmd.addAll(filePaths);
        return cmd;
    }

    @Override
    public boolean supportsEntry(String file) {
        if (file == null) return false;
        String f = file.toLowerCase();
        return f.endsWith(".bat") || f.endsWith(".cmd");
    }

    @Override
    public String resolveCommand() { return resolver.resolveCmd(); }
}
