package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NodeRunnerTest {

    @Test
    void typeIsNode() {
        NodeRunner r = new NodeRunner(new CommandResolver());
        assertEquals("node", r.runtimeType());
    }

    @Test
    void supportsJsEntries() {
        NodeRunner r = new NodeRunner(new CommandResolver());
        assertTrue(r.supportsEntry("main.js"));
        assertTrue(r.supportsEntry("app.mjs"));
        assertFalse(r.supportsEntry("main.py"));
    }

    @Test
    void buildCommandNoDashU() {
        NodeRunner r = new NodeRunner(new CommandResolver());
        List<String> cmd = r.buildCommand("main.js", Path.of("C:", "work", "data"),
                List.of("C:\\work\\data\\a.csv"));
        assertEquals("node", cmd.get(0));
        assertEquals("main.js", cmd.get(1));
        assertEquals(4, cmd.size());
    }
}
