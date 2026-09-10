package com.toolplatform.service;

import com.toolplatform.entity.RunLog;
import com.toolplatform.repository.RunLogRepository;
import com.toolplatform.service.ScriptRunnerService.ScriptRunResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RunLogService {

    private static final int MAX_FILE_NAMES_CHARS = 500;

    private final RunLogRepository repository;

    @Value("${runlog.retention-days:90}")
    private int retentionDays;

    public RunLogService(RunLogRepository repository) {
        this.repository = repository;
    }

    public void record(Long toolId, String toolName, Long userId,
                       String username, String nickname,
                       String runtime, boolean sandboxUsed,
                       ScriptRunResult result, List<String> inputFileNames) {
        RunLog rl = new RunLog();
        rl.setToolId(toolId);
        rl.setToolName(toolName);
        rl.setUserId(userId);
        rl.setUsername(username);
        rl.setNickname(nickname);
        rl.setRuntime(runtime == null || runtime.trim().isEmpty() ? "python" : runtime);
        rl.setSandboxUsed(sandboxUsed);
        rl.setExitCode(result.getExitCode());
        rl.setTimedOut(result.isTimedOut());
        rl.setStatus(deriveStatus(result));
        rl.setInputFileNames(truncateFileNames(inputFileNames));
        rl.setOutput(result.getOutput() == null ? "" : result.getOutput());
        repository.save(rl);
    }

    static String deriveStatus(ScriptRunResult r) {
        if (r.isTimedOut()) return "TIMEOUT";
        if (!r.isPythonFound()) return "RUNTIME_MISSING";
        return r.getExitCode() == 0 ? "SUCCESS" : "FAILED";
    }

    private String truncateFileNames(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        String joined = String.join(",", names);
        return joined.length() <= MAX_FILE_NAMES_CHARS ? joined : joined.substring(0, MAX_FILE_NAMES_CHARS);
    }
}
