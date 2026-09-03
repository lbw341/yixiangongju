package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void classpathWindowsSemicolon() throws Exception {
        Path payload = Files.createTempDirectory("jvp");
        Files.createDirectories(payload.resolve("lib"));
        String cp = JavaClasspath.build(payload);
        assertTrue(cp.startsWith(payload.toAbsolutePath().toString()));
        assertTrue(cp.contains("lib" + java.io.File.pathSeparator + "*"));
        assertTrue(cp.contains(";") || cp.contains(":"));
    }

    @Test
    void buildCommandUsesJarFlag() {
        JavaRunner r = new JavaRunner(new CommandResolver());
        List<String> cmd = r.buildCommand("app.jar", Path.of("C:", "data"),
                List.of("C:\\data\\a.csv"));
        assertEquals("java", cmd.get(0));
        assertEquals("-jar", cmd.get(1));
        assertEquals("app.jar", cmd.get(2));
        assertEquals(5, cmd.size());
    }
}
