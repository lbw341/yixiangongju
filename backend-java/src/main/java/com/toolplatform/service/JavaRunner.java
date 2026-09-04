package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

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
        Path scriptPath = Path.of(script).toAbsolutePath();
        String mainClass = readMainClass(scriptPath);
        if (mainClass != null) {
            cmd.add("-cp");
            cmd.add(classpathWithLibs(scriptPath));
            cmd.add(mainClass);
        } else {
            cmd.add("-jar");
            cmd.add(script);
        }
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

    /**
     * 读取可执行 jar 的 Main-Class。仅当入口 jar 旁存在 lib/（外部依赖，规格约定「Java 依赖仅 lib/*.jar」）
     * 时才需要，此时不能再用 -jar（-jar 忽略 -cp），需按 -cp entry.jar;lib/* MainClass 方式启动。
     * 入口 jar 无法解析或不存在 lib 时返回 null，调用方退回 -jar。
     */
    private static String readMainClass(Path scriptPath) {
        Path libDir = scriptPath.getParent() == null ? null
                : scriptPath.getParent().resolve("lib");
        if (libDir == null || !Files.isDirectory(libDir)) {
            return null;
        }
        try (JarFile jar = new JarFile(scriptPath.toFile())) {
            String mc = jar.getManifest() != null
                    ? jar.getManifest().getMainAttributes().getValue("Main-Class")
                    : null;
            return mc == null || mc.trim().isEmpty() ? null : mc.trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String classpathWithLibs(Path scriptPath) {
        return scriptPath.toString() + File.pathSeparator
                + scriptPath.getParent().resolve("lib").toAbsolutePath()
                + java.io.File.separator + "*";
    }
}
