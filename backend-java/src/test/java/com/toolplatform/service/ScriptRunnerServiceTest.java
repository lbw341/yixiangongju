package com.toolplatform.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptRunnerServiceTest {

    private final Path dir = Path.of("C:/srv/data").toAbsolutePath();

    @Test
    void resolveDataPathKeepsSubdirectories() {
        Path p = ScriptRunnerService.resolveDataPath(dir, "sub/folder/输入.csv");
        assertTrue(p.startsWith(dir));
        assertEquals(dir.resolve("sub/folder/输入.csv"), p);
    }

    @Test
    void resolveDataPathNormalizesBackslashes() {
        assertEquals(dir.resolve("a/b.txt"), ScriptRunnerService.resolveDataPath(dir, "a\\b.txt"));
    }

    @Test
    void resolveDataPathBlocksTraversal() {
        assertEquals(dir.resolve("evil.csv"), ScriptRunnerService.resolveDataPath(dir, "a/../evil.csv"));
        assertEquals(dir.resolve("evil.txt"), ScriptRunnerService.resolveDataPath(dir, "..\\evil.txt"));
    }

    @Test
    void resolveDataPathStripsAbsolutePaths() {
        assertEquals(dir.resolve("passwd"), ScriptRunnerService.resolveDataPath(dir, "/etc/passwd"));
        assertEquals(dir.resolve("file.txt"), ScriptRunnerService.resolveDataPath(dir, "C:/windows/file.txt"));
    }

    @Test
    void resolveDataPathSanitizesIllegalChars() {
        Path p = ScriptRunnerService.resolveDataPath(dir, "a?b/c*d.txt");
        assertFalse(p.toString().contains("?"));
        assertFalse(p.toString().contains("*"));
        assertTrue(p.startsWith(dir));
    }

    @Test
    void resolveDataPathFallsBackForEmptyName() {
        Path p = ScriptRunnerService.resolveDataPath(dir, "");
        assertFalse(p.equals(dir));
        assertTrue(p.startsWith(dir));
        assertFalse(p.equals(dir.resolve(".")));
    }

    @Test
    void runDispatchesUnknownRuntimeToRuntimeMissing() throws Exception {
        ScriptRunnerService svc = new ScriptRunnerService(new CommandResolver(),
                List.of(new PythonRunner()));
        Path script = Files.createTempFile("s", ".py");
        ScriptRunnerService.ScriptRunResult r = svc.run("go", script, new java.util.HashMap<>());
        assertFalse(r.isPythonFound());
        Files.deleteIfExists(script);
    }
}
