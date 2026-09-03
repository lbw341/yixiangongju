package com.toolplatform.service;

import java.nio.file.Path;

public final class JavaClasspath {
    private JavaClasspath() {}

    public static String build(Path payload) {
        String sep = System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":";
        return payload.toAbsolutePath() + sep + payload.toAbsolutePath().resolve("lib") + sep + "*";
    }
}
