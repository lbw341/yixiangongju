package com.toolplatform.controller;

import com.toolplatform.entity.Tool;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolControllerTest {

    @Test
    void toToolMapIncludesRuntime() throws Exception {
        Tool t = new Tool();
        t.setId(1L); t.setName("n"); t.setType("python"); t.setCategory("c");
        t.setRuntime("node");
        Method m = ToolController.class.getDeclaredMethod("toToolMapInternal", Tool.class);
        m.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) m.invoke(null, t);
        assertEquals("node", map.get("runtime"));
    }
}
