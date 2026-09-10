package com.toolplatform.service;

import com.toolplatform.entity.RunLog;
import com.toolplatform.repository.RunLogRepository;
import com.toolplatform.service.ScriptRunnerService.ScriptRunResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunLogServiceTest {

    private RunLogService newService(RunLogRepository repo) {
        return new RunLogService(repo);
    }

    @Test
    void deriveStatus_mapsAllFourStates() {
        assertEquals("SUCCESS", RunLogService.deriveStatus(ScriptRunResult.of(0, "", "python")));
        assertEquals("FAILED", RunLogService.deriveStatus(ScriptRunResult.of(1, "", "python")));
        assertEquals("TIMEOUT", RunLogService.deriveStatus(ScriptRunResult.timedOut("slow", "python")));
        assertEquals("RUNTIME_MISSING", RunLogService.deriveStatus(ScriptRunResult.pythonMissing()));
        assertEquals("RUNTIME_MISSING", RunLogService.deriveStatus(ScriptRunResult.runtimeMissing("bash")));
    }

    @Test
    void record_persistsFullFields() {
        RunLogRepository repo = mock(RunLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        newService(repo).record(1L, "报表生成", 3L, "zhang", "小张",
                "bash", true, ScriptRunResult.of(0, "out", "bash"), List.of("a.csv", "b.txt"));

        ArgumentCaptor<RunLog> cap = ArgumentCaptor.forClass(RunLog.class);
        verify(repo).save(cap.capture());
        RunLog rl = cap.getValue();
        assertEquals(1L, rl.getToolId());
        assertEquals("报表生成", rl.getToolName());
        assertEquals(3L, rl.getUserId());
        assertEquals("zhang", rl.getUsername());
        assertEquals("小张", rl.getNickname());
        assertEquals("bash", rl.getRuntime());
        assertTrue(rl.isSandboxUsed());
        assertEquals(0, rl.getExitCode());
        assertFalse(rl.isTimedOut());
        assertEquals("SUCCESS", rl.getStatus());
        assertEquals("a.csv,b.txt", rl.getInputFileNames());
        assertEquals("out", rl.getOutput());
        assertNotNull(rl.getCreatedAt());
    }

    @Test
    void record_nullRuntimeNormalizesToPython() {
        RunLogRepository repo = mock(RunLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        newService(repo).record(1L, "t", 2L, "u", "n", null, false,
                ScriptRunResult.of(0, "", "python"), List.of());
        ArgumentCaptor<RunLog> cap = ArgumentCaptor.forClass(RunLog.class);
        verify(repo).save(cap.capture());
        assertEquals("python", cap.getValue().getRuntime());
    }

    @Test
    void record_truncatesInputFileNamesTo500Chars() {
        RunLogRepository repo = mock(RunLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String longName = "f".repeat(600);
        newService(repo).record(1L, "t", 2L, "u", "n", "python", false,
                ScriptRunResult.of(0, "", "python"), List.of(longName));
        ArgumentCaptor<RunLog> cap = ArgumentCaptor.forClass(RunLog.class);
        verify(repo).save(cap.capture());
        assertEquals(500, cap.getValue().getInputFileNames().length());
    }
}
