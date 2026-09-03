package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScriptPackageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void entryCandidatesContainJsForNode() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("node")).contains("main.js"));
    }

    @Test
    void entryCandidatesPyForPython() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("python")).contains("main.py"));
    }

    @Test
    void entryCandidatesJarForJava() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("java")).contains("app.jar"));
    }

    @Test
    void copyJarLibsCopiesJarsButNotNonJars() throws Exception {
        Path payload = tempDir.resolve("payload");
        Path libDir = payload.resolve("lib");
        Files.createDirectories(libDir);
        Files.write(libDir.resolve("a.jar"), new byte[]{1, 2, 3});
        Files.write(libDir.resolve("notes.txt"), "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Path jarpkgRoot = tempDir.resolve("jarpkgs");
        ScriptPackageService service = new ScriptPackageService(null);
        injectJarpkgDir(service, jarpkgRoot.toAbsolutePath().toString());

        service.copyJarLibs(42L, payload);

        Path copied = jarpkgRoot.resolve("42").resolve("lib").resolve("a.jar");
        assertTrue(Files.isRegularFile(copied), "expected a.jar to be copied");

        Path nonJar = jarpkgRoot.resolve("42").resolve("lib").resolve("notes.txt");
        assertFalse(Files.exists(nonJar), "non-jar files must not be copied");
    }

    private void injectJarpkgDir(ScriptPackageService service, String value) throws Exception {
        java.lang.reflect.Field f = ScriptPackageService.class.getDeclaredField("jarpkgDir");
        f.setAccessible(true);
        f.set(service, value);
    }
}
