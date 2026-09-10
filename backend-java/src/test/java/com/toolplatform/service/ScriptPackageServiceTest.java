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
    void entryCandidatesContainShForBash() {
        String[] candidates = ScriptPackageService.entryCandidatesFor("bash");
        java.util.List<String> list = java.util.Arrays.asList(candidates);
        assertTrue(list.contains("main.sh"), "bash candidates should contain main.sh");
        assertTrue(list.contains("app.sh"), "bash candidates should contain app.sh");
        assertTrue(list.contains("run.sh"), "bash candidates should contain run.sh");
        assertTrue(list.contains("index.sh"), "bash candidates should contain index.sh");
        assertEquals(4, candidates.length, "bash should have 4 candidates");
    }

    @Test
    void entryCandidatesContainBatForBat() {
        String[] candidates = ScriptPackageService.entryCandidatesFor("bat");
        java.util.List<String> list = java.util.Arrays.asList(candidates);
        assertTrue(list.contains("main.bat"), "bat candidates should contain main.bat");
        assertTrue(list.contains("app.bat"), "bat candidates should contain app.bat");
        assertTrue(list.contains("run.bat"), "bat candidates should contain run.bat");
        assertEquals(3, candidates.length, "bat should have 3 candidates");
    }

    @Test
    void entryExtMatchesForBashRuntime() throws Exception {
        ScriptPackageService service = new ScriptPackageService(null);
        java.lang.reflect.Method m = ScriptPackageService.class.getDeclaredMethod("entryExtMatches", String.class, String.class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(service, "main.sh", "bash"), ".sh should match bash");
        assertTrue((Boolean) m.invoke(service, "script.BASH", "bash"), ".bash should match bash");
        assertFalse((Boolean) m.invoke(service, "main.py", "bash"), ".py should not match bash");
    }

    @Test
    void entryExtMatchesForBatRuntime() throws Exception {
        ScriptPackageService service = new ScriptPackageService(null);
        java.lang.reflect.Method m = ScriptPackageService.class.getDeclaredMethod("entryExtMatches", String.class, String.class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(service, "main.bat", "bat"), ".bat should match bat");
        assertTrue((Boolean) m.invoke(service, "script.CMD", "bat"), ".cmd should match bat");
        assertFalse((Boolean) m.invoke(service, "main.py", "bat"), ".py should not match bat");
    }

    @Test
    void entryExtMatchesExistingRuntimesNotRegressed() throws Exception {
        ScriptPackageService service = new ScriptPackageService(null);
        java.lang.reflect.Method m = ScriptPackageService.class.getDeclaredMethod("entryExtMatches", String.class, String.class);
        m.setAccessible(true);
        assertTrue((Boolean) m.invoke(service, "main.py", "python"));
        assertTrue((Boolean) m.invoke(service, "main.js", "node"));
        assertTrue((Boolean) m.invoke(service, "main.mjs", "node"));
        assertTrue((Boolean) m.invoke(service, "app.jar", "java"));
        assertFalse((Boolean) m.invoke(service, "main.sh", "python"));
        assertFalse((Boolean) m.invoke(service, "main.bat", "python"));
    }

    @Test
    void checkBlockedBatAllowedForBatRuntime() throws Exception {
        ScriptPackageService service = new ScriptPackageService(null);
        java.lang.reflect.Method m = ScriptPackageService.class.getDeclaredMethod("checkBlocked", String.class, String.class);
        m.setAccessible(true);
        m.invoke(service, "script.bat", "bat");
        m.invoke(service, "script.cmd", "bat");
    }

    @Test
    void checkBlockedBatStillBlockedForOtherRuntimes() throws Exception {
        ScriptPackageService service = new ScriptPackageService(null);
        java.lang.reflect.Method m = ScriptPackageService.class.getDeclaredMethod("checkBlocked", String.class, String.class);
        m.setAccessible(true);
        assertThrows(Exception.class, () -> m.invoke(service, "script.bat", "python"));
        assertThrows(Exception.class, () -> m.invoke(service, "script.bat", "node"));
        assertThrows(Exception.class, () -> m.invoke(service, "script.bat", "bash"));
        assertThrows(Exception.class, () -> m.invoke(service, "script.bat", "java"));
        assertThrows(Exception.class, () -> m.invoke(service, "script.cmd", "python"));
        assertThrows(Exception.class, () -> m.invoke(service, "script.cmd", "node"));
    }

    @Test
    void checkBlockedJarStillAllowedOnlyForJava() throws Exception {
        ScriptPackageService service = new ScriptPackageService(null);
        java.lang.reflect.Method m = ScriptPackageService.class.getDeclaredMethod("checkBlocked", String.class, String.class);
        m.setAccessible(true);
        m.invoke(service, "app.jar", "java");
        assertThrows(Exception.class, () -> m.invoke(service, "app.jar", "python"));
        assertThrows(Exception.class, () -> m.invoke(service, "app.jar", "bat"));
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
