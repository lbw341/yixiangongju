package com.toolplatform.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** BashRunner 单测 + Git Bash 真子进程 E2E（bash 不存在时优雅跳过） */
class BashRunnerTest {

    private static ScriptRunnerService newSvcWithBash() {
        return new ScriptRunnerService(new CommandResolver(),
                List.of(new BashRunner(new CommandResolver())));
    }

    @Test
    void typeIsBash() {
        BashRunner r = new BashRunner(new CommandResolver());
        assertEquals("bash", r.runtimeType());
    }

    @Test
    void supportsShBashEntries() {
        BashRunner r = new BashRunner(new CommandResolver());
        assertTrue(r.supportsEntry("main.sh"));
        assertTrue(r.supportsEntry("run.bash"));
        assertTrue(r.supportsEntry("DIR/main.SH"));
        assertFalse(r.supportsEntry("main.py"));
        assertFalse(r.supportsEntry("main.bat"));
        assertFalse(r.supportsEntry(null));
    }

    @Test
    void buildCommandShape_noDashU_scriptDataDirFiles() {
        BashRunner r = new BashRunner(new CommandResolver());
        String bash = r.resolveCommand();
        Path dataDir = Path.of("C:", "work", "data");
        List<String> cmd = r.buildCommand("main.sh", dataDir,
                List.of("C:\\work\\data\\a.csv", "C:\\work\\data\\b.csv"));
        assertEquals(bash, cmd.get(0));
        assertEquals("main.sh", cmd.get(1));
        assertEquals(dataDir.toAbsolutePath().toString(), cmd.get(2));
        assertEquals("C:\\work\\data\\a.csv", cmd.get(3));
        assertEquals("C:\\work\\data\\b.csv", cmd.get(4));
        assertEquals(5, cmd.size());
    }

    @Test
    void bashReadsArgvAndEnv() throws Exception {
        CommandResolver cr = new CommandResolver();
        String bash = cr.resolveBash();
        if (bash == null) {
            System.out.println("bash 未安装，跳过 bash E2E");
            return;
        }
        Path script = Files.createTempFile("smoke", ".sh");
        Files.writeString(script,
                "#!/bin/bash\n" +
                "echo \"ARGVDIR=$1\"\n" +
                "if [ -n \"$2\" ]; then echo \"NAME=$(cat \"$2\")\"; else echo \"NAME=none\"; fi\n" +
                "echo \"ENVDIR=$DATA_DIR\"\n");
        try {
            Map<String, byte[]> data = new LinkedHashMap<>();
            data.put("hello.txt", "hi-bash".getBytes());
            ScriptRunnerService.ScriptRunResult r = newSvcWithBash().runBy("bash", script, data);
            assertFalse(r.getExitCode() == -1 && r.getOutput().isEmpty(), "bash 运行失败: " + r.getRuntime());
            assertEquals(0, r.getExitCode(), "stderr/stdout was: " + r.getOutput());
            assertTrue(r.getOutput().contains("ARGVDIR="), "output: " + r.getOutput());
            assertTrue(r.getOutput().contains("NAME=hi-bash"), "output: " + r.getOutput());
            assertTrue(r.getOutput().contains("ENVDIR="), "output: " + r.getOutput());
        } finally {
            Files.deleteIfExists(script);
        }
    }
}
