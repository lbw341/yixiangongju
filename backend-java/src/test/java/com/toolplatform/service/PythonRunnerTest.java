package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PythonRunnerTest {

    @Test
    void typeIsPython() {
        PythonRunner r = new PythonRunner();
        assertEquals("python", r.runtimeType());
    }

    @Test
    void supportsPyEntryFiles() {
        PythonRunner r = new PythonRunner();
        assertTrue(r.supportsEntry("main.py"));
        assertFalse(r.supportsEntry("main.js"));
        assertFalse(r.supportsEntry("app.jar"));
    }

    @Test
    void buildCommandKeepsLegacyShape() {
        PythonRunner r = new PythonRunner();
        List<String> cmd = r.buildCommand("main.py", Path.of("C:", "work", "data"),
                List.of("C:\\work\\data\\a.csv", "C:\\work\\data\\b.csv"));
        assertEquals("python", cmd.get(0));
        assertEquals("-u", cmd.get(1));
        assertEquals("main.py", cmd.get(2));
        assertEquals(2, cmd.size() - 4);
    }
}
