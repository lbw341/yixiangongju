package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class JavaRunnerTest {

    @Test
    void typeIsJava() {
        JavaRunner r = new JavaRunner(new CommandResolver());
        assertEquals("java", r.runtimeType());
    }

    @Test
    void supportsJarOnly() {
        JavaRunner r = new JavaRunner(new CommandResolver());
        assertTrue(r.supportsEntry("app.jar"));
        assertFalse(r.supportsEntry("Main.java"));
        assertFalse(r.supportsEntry("main.py"));
    }

    @Test
    void buildCommandWithoutLibUsesJarFlag() throws Exception {
        JavaRunner r = new JavaRunner(new CommandResolver());
        List<String> cmd = r.buildCommand("app.jar", Path.of("C:", "data"),
                List.of("C:\\data\\a.csv"));
        assertEquals("java", cmd.get(0));
        assertEquals("-jar", cmd.get(1));
        assertEquals("app.jar", cmd.get(2));
        assertEquals(5, cmd.size());
    }

    @Test
    void buildCommandWithSiblingLibUsesClasspathAndMainClass() throws Exception {
        Path tmp = Files.createTempDirectory("jr_lib");
        try {
            Path entryJar = tmp.resolve("app.jar");
            writeJar(entryJar, "com.example.App");
            Files.createDirectories(tmp.resolve("lib"));
            Files.write(tmp.resolve("lib").resolve("dep.txt"), new byte[]{1});

            JavaRunner r = new JavaRunner(new CommandResolver());
            List<String> cmd = r.buildCommand(entryJar.toString(), Path.of("C:", "data"),
                    List.of("C:\\data\\a.csv"));
            assertEquals("java", cmd.get(0));
            assertEquals("-cp", cmd.get(1));
            String cp = cmd.get(2);
            assertEquals(entryJar.toAbsolutePath().toString() + File.pathSeparator
                    + tmp.resolve("lib").toAbsolutePath() + File.separatorChar + "*", cp);
            assertEquals("com.example.App", cmd.get(3));
            assertEquals("C:\\data", cmd.get(4));
            assertEquals("C:\\data\\a.csv", cmd.get(5));
            assertEquals(6, cmd.size());
        } finally {
            deleteRecursive(tmp);
        }
    }

    @Test
    void buildCommandWithLibButUnreadableJarFallsBackToJarFlag() throws Exception {
        Path tmp = Files.createTempDirectory("jr_badjar");
        try {
            Path entryJar = tmp.resolve("app.jar");
            Files.write(entryJar, "not a real jar".getBytes());
            Files.createDirectories(tmp.resolve("lib"));

            JavaRunner r = new JavaRunner(new CommandResolver());
            List<String> cmd = r.buildCommand(entryJar.toString(), Path.of("C:", "data"), List.of());
            assertEquals("java", cmd.get(0));
            assertEquals("-jar", cmd.get(1));
        } finally {
            deleteRecursive(tmp);
        }
    }

    private static void writeJar(Path jarPath, String mainClass) throws IOException {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()), mf)) {
            jos.putNextEntry(new java.util.zip.ZipEntry("Empty.class"));
            jos.write(new byte[]{1});
            jos.closeEntry();
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                for (Path entry : (Iterable<Path>) entries::iterator) {
                    deleteRecursive(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
