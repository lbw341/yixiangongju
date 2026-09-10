package com.toolplatform.service;

import com.toolplatform.entity.RunLog;
import com.toolplatform.repository.RunLogRepository;
import com.toolplatform.service.ScriptRunnerService.ScriptRunResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import com.toolplatform.service.RunLogService.PageResult;
import com.toolplatform.service.RunLogService.RunLogDetail;
import com.toolplatform.service.RunLogService.RunLogQuery;
import com.toolplatform.service.RunLogService.RunLogSummary;

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

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = RunLogService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** 继承 JpaRepository：未用方法返回默认值；run_logs 专属方法基于内存 List 模拟 */
    private RunLogRepository fakeRepo() {
        return new RunLogRepository() {
            private final List<RunLog> store = new ArrayList<>();

            @Override public <S extends RunLog> S save(S e) { store.add(e); return e; }
            @Override public <S extends RunLog> S saveAndFlush(S e) { return save(e); }
            @Override public <S extends RunLog> List<S> saveAllAndFlush(Iterable<S> es) { return List.of(); }
            @Override public boolean existsById(Long id) { return store.stream().anyMatch(r -> r.getId() != null && r.getId().equals(id)); }
            @Override public Optional<RunLog> findById(Long id) {
                return store.stream().filter(r -> r.getId() != null && r.getId().equals(id)).findFirst();
            }
            @Override public List<RunLog> findAll() { return List.of(); }
            @Override public List<RunLog> findAllById(Iterable<Long> ids) { return List.of(); }
            @Override public long count() { return store.size(); }
            @Override public void deleteById(Long id) { store.removeIf(r -> r.getId() != null && r.getId().equals(id)); }
            @Override public void delete(RunLog e) { store.remove(e); }
            @Override public void deleteAllById(Iterable<? extends Long> ids) { }
            @Override public void deleteAll() { }
            @Override public void deleteAll(Iterable<? extends RunLog> es) { }
            @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { }
            @Override public void deleteInBatch(Iterable<RunLog> es) { }
            @Override public void deleteAllInBatch() { }
            @Override public void deleteAllInBatch(Iterable<RunLog> es) { }
            @Override public void flush() { }
            @Override public RunLog getReferenceById(Long id) { return null; }
            @Override public RunLog getById(Long id) { return null; }
            @Override public RunLog getOne(Long id) { return null; }
            @Override public Page<RunLog> findAll(Pageable pageable) { return new PageImpl<>(List.of(), pageable, 0); }
            @Override public <S extends RunLog> List<S> findAll(Example<S> e) { return List.of(); }
            @Override public <S extends RunLog> List<S> findAll(Example<S> e, Sort s) { return List.of(); }
            @Override public <S extends RunLog> Page<S> findAll(Example<S> e, Pageable p) { return Page.empty(); }
            @Override public <S extends RunLog> long count(Example<S> e) { return 0; }
            @Override public <S extends RunLog> boolean exists(Example<S> e) { return false; }
            @Override public <S extends RunLog> Optional<S> findOne(Example<S> e) { return Optional.empty(); }
            @Override public <S extends RunLog, R> R findBy(
                    Example<S> e, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> q) {
                return null;
            }
            @Override public List<RunLog> findAll(Sort sort) { return List.of(); }
            @Override public <S extends RunLog> List<S> saveAll(Iterable<S> es) { return List.of(); }
            @Override public long deleteByCreatedAtBefore(LocalDateTime cutoff) {
                int before = store.size();
                store.removeIf(r -> r.getCreatedAt() != null && r.getCreatedAt().isBefore(cutoff));
                return before - store.size();
            }
            @Override public Page<RunLog> search(String tool, String user, LocalDateTime start,
                                                 LocalDateTime end, String status, Pageable pageable) {
                List<RunLog> filtered = store.stream()
                    .filter(r -> tool == null || tool.isEmpty()
                            || (r.getToolName() != null && r.getToolName().contains(tool)))
                    .filter(r -> user == null || user.isEmpty()
                            || (r.getUsername() != null && r.getUsername().contains(user))
                            || (r.getNickname() != null && r.getNickname().contains(user)))
                    .filter(r -> start == null || (r.getCreatedAt() != null && !r.getCreatedAt().isBefore(start)))
                    .filter(r -> end == null || (r.getCreatedAt() != null && r.getCreatedAt().isBefore(end)))
                    .filter(r -> status == null || status.isEmpty() || status.equals(r.getStatus()))
                    .sorted(Comparator.comparing(RunLog::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .toList();
                int from = (int) pageable.getOffset();
                int to = Math.min(from + pageable.getPageSize(), filtered.size());
                List<RunLog> content = from >= filtered.size() ? List.of() : filtered.subList(from, to);
                return new PageImpl<>(content, pageable, filtered.size());
            }
        };
    }

    private void saveLog(RunLogRepository repo, long id, String toolName, String username, String nickname,
                         String runtime, boolean sandbox, int exit, boolean timedOut, String status,
                         LocalDateTime createdAt, String files) {
        RunLog rl = new RunLog();
        rl.setId(id); rl.setToolId(id); rl.setToolName(toolName);
        rl.setUserId(1L); rl.setUsername(username); rl.setNickname(nickname);
        rl.setRuntime(runtime); rl.setSandboxUsed(sandbox);
        rl.setExitCode(exit); rl.setTimedOut(timedOut); rl.setStatus(status);
        rl.setInputFileNames(files); rl.setCreatedAt(createdAt);
        repo.save(rl);
    }

    @Test
    void query_filtersAndPaginationCombine() {
        RunLogRepository repo = fakeRepo();
        RunLogService svc = newService(repo);

        LocalDateTime base = LocalDateTime.of(2026, 9, 1, 10, 0);
        saveLog(repo, 1L, "报表生成", "zhang", "小张", "python", true, 0, false, "SUCCESS", base, "a.csv");
        saveLog(repo, 2L, "报表生成", "zhang", "小张", "python", true, 1, false, "FAILED", base.plusHours(1), "b.csv");
        saveLog(repo, 3L, "巡检工具", "wang", "王五", "bash", true, 0, false, "SUCCESS", base.plusHours(2), "c.log");
        saveLog(repo, 4L, "巡检工具", "wang", "王五", "bash", true, 0, false, "SUCCESS", base.plusHours(3), "d.log");

        // tool 模糊
        PageResult<RunLogSummary> r = svc.query(new RunLogQuery("巡检", null, null, null, null), 1, 10);
        assertEquals(2, r.getTotal());
        assertEquals("巡检工具", r.getItems().get(0).getToolName());

        // user 模糊（昵称命中）
        assertEquals(2, svc.query(new RunLogQuery(null, "王五", null, null, null), 1, 10).getTotal());
        assertEquals(2, svc.query(new RunLogQuery(null, "zhang", null, null, null), 1, 10).getTotal());

        // status 精确
        assertEquals(1, svc.query(new RunLogQuery(null, null, null, null, "FAILED"), 1, 10).getTotal());
        // 非法 status 无结果（精确匹配，非模糊）
        assertEquals(0, svc.query(new RunLogQuery(null, null, null, null, "SUCCES"), 1, 10).getTotal());

        // 时间范围：start 含起点
        assertEquals(2, svc.query(new RunLogQuery(null, null, base.plusHours(2), null, null), 1, 10).getTotal());
        // 时间范围：end 排他
        assertEquals(2, svc.query(new RunLogQuery(null, null, null, base.plusHours(2), null), 1, 10).getTotal());

        // 组合筛选
        assertEquals(1, svc.query(new RunLogQuery(null, "zhang", null, null, "SUCCESS"), 1, 10).getTotal());
        assertEquals(2, svc.query(new RunLogQuery("工", "王五", null, null, null), 1, 10).getTotal());

        // 倒序 + 分页
        PageResult<RunLogSummary> page1 = svc.query(new RunLogQuery(null, null, null, null, null), 1, 3);
        assertEquals(4, page1.getTotal());
        assertEquals(3, page1.getItems().size());
        assertEquals(4L, page1.getItems().get(0).getId());
        assertEquals(3L, page1.getItems().get(1).getId());
        PageResult<RunLogSummary> page2 = svc.query(new RunLogQuery(null, null, null, null, null), 2, 3);
        assertEquals(1, page2.getItems().size());
        assertEquals(1L, page2.getItems().get(0).getId());
    }

    @Test
    void query_clampsPageAndSize() {
        RunLogRepository repo = fakeRepo();
        saveLog(repo, 1L, "t", "u", "n", "python", true, 0, false, "SUCCESS", LocalDateTime.now(), "a");
        RunLogService svc = newService(repo);
        PageResult<RunLogSummary> r = svc.query(new RunLogQuery(null, null, null, null, null), 0, 999);
        assertEquals(1, r.getPage());
        assertEquals(100, r.getSize());
        assertEquals(1, r.getTotal());
    }

    @Test
    void query_summaryExcludesOutputButCountsFiles() {
        RunLogRepository repo = fakeRepo();
        saveLog(repo, 5L, "t", "u", "n", "python", true, 0, false, "SUCCESS", LocalDateTime.now(), "x.csv,y.txt");
        saveLog(repo, 6L, "t", "u", "n", "python", true, 0, false, "SUCCESS", LocalDateTime.now().minusHours(1), "");
        RunLogService svc = newService(repo);
        PageResult<RunLogSummary> r = svc.query(new RunLogQuery(null, null, null, null, null), 1, 10);
        assertEquals(2, r.getItems().size());
        RunLogSummary first = r.getItems().get(0);
        assertEquals(2, first.getInputFileCount());
        // RunLogSummary 没有 getOutput——列表项天然不含 output（spec §4.1）
        RunLogSummary second = r.getItems().get(1);
        assertEquals(0, second.getInputFileCount());
    }

    @Test
    void getDetail_mapsFullDetailOrNull() {
        RunLogRepository repo = fakeRepo();
        saveLog(repo, 7L, "t", "u", "n", "python", true, 0, false, "SUCCESS", LocalDateTime.now(), "a.csv");
        // In-memory fake keeps same reference; set output on the saved row
        repo.findById(7L).ifPresent(r -> r.setOutput("hello output"));
        RunLogService svc = newService(repo);
        RunLogDetail d = svc.getDetail(7L);
        assertNotNull(d);
        assertEquals(7L, d.getId());
        assertEquals(7L, d.getToolId());
        assertEquals("a.csv", d.getInputFileNames());
        assertEquals(1, d.getInputFileCount());
        // RunLogDetail 扩展了 RunLogSummary
        assertEquals("SUCCESS", d.getStatus());
        assertEquals("hello output", d.getOutput());
        assertNull(svc.getDetail(99L));
    }

    @Test
    void cleanupExpired_deletesOnlyExpiredByRetention() throws Exception {
        RunLogRepository repo = fakeRepo();
        saveLog(repo, 1L, "t", "u", "n", "python", true, 0, false, "SUCCESS", LocalDateTime.now().minusDays(100), "a");
        saveLog(repo, 2L, "t", "u", "n", "python", true, 0, false, "SUCCESS", LocalDateTime.now().minusDays(10), "b");
        RunLogService svc = newService(repo);
        setField(svc, "retentionDays", 90);
        assertEquals(1, svc.cleanupExpired());
        assertEquals(1, repo.count());
        assertTrue(repo.findById(2L).isPresent());
        assertTrue(repo.findById(1L).isEmpty());
    }
}
