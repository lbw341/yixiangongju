package com.toolplatform.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolTest {

    @Test
    void runtimeDefaultsToPython() {
        Tool t = new Tool();
        assertEquals("python", t.getRuntime());
    }

    @Test
    void runtimeCanBeSetToNode() {
        Tool t = new Tool();
        t.setRuntime("node");
        assertEquals("node", t.getRuntime());
    }
}
