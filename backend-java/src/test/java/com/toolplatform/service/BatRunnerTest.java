package com.toolplatform.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** BatRunner 单测：cmd.exe /c 命令形态 + 入口识别 */
class BatRunnerTest {

    @Test
    void typeIsBat() {
        BatRunner r = new BatRunner(new CommandResolver());
        assertEquals("bat", r.runtimeType());
    }

    @Test
    void supportsBatCmdEntries() {
        BatRunner r = new BatRunner(new CommandResolver());
        assertTrue(r.supportsEntry("run.bat"));
        assertTrue(r.supportsEntry("run.cmd"));
        assertTrue(r.supportsEntry("DIR/run.BAT"));
        assertFalse(r.supportsEntry("main.py"));
        assertFalse(r.supportsEntry("main.sh"));
        assertFalse(r.supportsEntry(null));
    }

    @Test
    void buildCommandShape_cmdSlashCScriptDataDirFiles() {
        BatRunner r = new BatRunner(new CommandResolver());
        String cmdExe = r.resolveCommand();
        Path dataDir = Path.of("C:", "work", "data");
        List<String> cmd = r.buildCommand("run.bat", dataDir,
                List.of("C:\\work\\data\\a.csv", "C:\\work\\data\\b.csv"));
        assertEquals(cmdExe, cmd.get(0));
        assertEquals("/c", cmd.get(1));
        assertEquals("run.bat", cmd.get(2));
        assertEquals(dataDir.toAbsolutePath().toString(), cmd.get(3));
        assertEquals("C:\\work\\data\\a.csv", cmd.get(4));
        assertEquals("C:\\work\\data\\b.csv", cmd.get(5));
        assertEquals(6, cmd.size());
    }
}
