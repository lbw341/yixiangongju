package com.toolplatform.service;

import com.toolplatform.entity.RunLog;
import com.toolplatform.repository.RunLogRepository;
import com.toolplatform.service.ScriptRunnerService.ScriptRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RunLogService {

    private static final Logger log = LoggerFactory.getLogger(RunLogService.class);
    private static final int MAX_FILE_NAMES_CHARS = 500;
    private static final int MAX_PAGE_SIZE = 100;

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

    public PageResult<RunLogSummary> query(RunLogQuery query, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(p - 1, s, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RunLog> result = repository.search(
                query == null ? null : query.tool,
                query == null ? null : query.user,
                query == null ? null : query.start,
                query == null ? null : query.endExclusive,
                query == null ? null : query.status,
                pageable);
        List<RunLogSummary> items = result.getContent().stream().map(this::toSummary).toList();
        return new PageResult<>(items, result.getTotalElements(), p, s);
    }

    public RunLogDetail getDetail(long id) {
        return repository.findById(id).map(rl -> {
            RunLogDetail d = new RunLogDetail();
            d.setId(rl.getId());
            d.setToolId(rl.getToolId());
            d.setToolName(rl.getToolName());
            d.setUserId(rl.getUserId());
            d.setUsername(rl.getUsername());
            d.setNickname(rl.getNickname());
            d.setRuntime(rl.getRuntime());
            d.setSandboxUsed(rl.isSandboxUsed());
            d.setExitCode(rl.getExitCode());
            d.setTimedOut(rl.isTimedOut());
            d.setStatus(rl.getStatus());
            d.setInputFileCount(countFileNames(rl.getInputFileNames()));
            d.setCreatedAt(rl.getCreatedAt());
            d.setInputFileNames(rl.getInputFileNames());
            d.setOutput(rl.getOutput());
            return d;
        }).orElse(null);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public int cleanupExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long deleted = repository.deleteByCreatedAtBefore(cutoff);
        log.info("运行日志清理：删除 {} 条，剩余 {} 条", deleted, repository.count());
        return (int) deleted;
    }

    private RunLogSummary toSummary(RunLog rl) {
        RunLogSummary s = new RunLogSummary();
        s.setId(rl.getId());
        s.setToolId(rl.getToolId());
        s.setToolName(rl.getToolName());
        s.setUserId(rl.getUserId());
        s.setUsername(rl.getUsername());
        s.setNickname(rl.getNickname());
        s.setRuntime(rl.getRuntime());
        s.setSandboxUsed(rl.isSandboxUsed());
        s.setExitCode(rl.getExitCode());
        s.setTimedOut(rl.isTimedOut());
        s.setStatus(rl.getStatus());
        s.setInputFileCount(countFileNames(rl.getInputFileNames()));
        s.setCreatedAt(rl.getCreatedAt());
        return s;
    }

    private int countFileNames(String names) {
        if (names == null || names.trim().isEmpty()) return 0;
        return (int) java.util.Arrays.stream(names.split(","))
                .filter(n -> !n.trim().isEmpty()).count();
    }

    private String truncateFileNames(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        String joined = String.join(",", names);
        return joined.length() <= MAX_FILE_NAMES_CHARS ? joined : joined.substring(0, MAX_FILE_NAMES_CHARS);
    }

    public static class RunLogQuery {
        public String tool;
        public String user;
        public LocalDateTime start;
        public LocalDateTime endExclusive;
        public String status;

        public RunLogQuery(String tool, String user, LocalDateTime start,
                           LocalDateTime endExclusive, String status) {
            this.tool = tool;
            this.user = user;
            this.start = start;
            this.endExclusive = endExclusive;
            this.status = status;
        }
    }

    public static class RunLogSummary {
        private Long id;
        private Long toolId;
        private String toolName;
        private Long userId;
        private String username;
        private String nickname;
        private String runtime;
        private boolean sandboxUsed;
        private int exitCode;
        private boolean timedOut;
        private String status;
        private int inputFileCount;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getToolId() { return toolId; }
        public void setToolId(Long toolId) { this.toolId = toolId; }
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getRuntime() { return runtime; }
        public void setRuntime(String runtime) { this.runtime = runtime; }
        public boolean isSandboxUsed() { return sandboxUsed; }
        public void setSandboxUsed(boolean sandboxUsed) { this.sandboxUsed = sandboxUsed; }
        public int getExitCode() { return exitCode; }
        public void setExitCode(int exitCode) { this.exitCode = exitCode; }
        public boolean isTimedOut() { return timedOut; }
        public void setTimedOut(boolean timedOut) { this.timedOut = timedOut; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getInputFileCount() { return inputFileCount; }
        public void setInputFileCount(int inputFileCount) { this.inputFileCount = inputFileCount; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    public static class RunLogDetail extends RunLogSummary {
        private String inputFileNames;
        private String output;

        public String getInputFileNames() { return inputFileNames; }
        public void setInputFileNames(String inputFileNames) { this.inputFileNames = inputFileNames; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
    }

    public static class PageResult<T> {
        private final List<T> items;
        private final long total;
        private final int page;
        private final int size;

        public PageResult(List<T> items, long total, int page, int size) {
            this.items = items;
            this.total = total;
            this.page = page;
            this.size = size;
        }

        public List<T> getItems() { return items; }
        public long getTotal() { return total; }
        public int getPage() { return page; }
        public int getSize() { return size; }
    }
}
