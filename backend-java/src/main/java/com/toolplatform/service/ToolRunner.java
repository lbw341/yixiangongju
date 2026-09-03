package com.toolplatform.service;

import java.nio.file.Path;
import java.util.List;

public interface ToolRunner {
    String runtimeType();
    String resolveCommand();
    boolean supportsEntry(String file);
    List<String> buildCommand(String script, Path dataDir, List<String> filePaths);
}
