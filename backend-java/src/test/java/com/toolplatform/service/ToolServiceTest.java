package com.toolplatform.service;

import com.toolplatform.entity.Tool;
import com.toolplatform.sandbox.SandboxExecutionService;
import com.toolplatform.service.ToolService.ZipExtractResult;
import com.toolplatform.service.ToolService.FileProcessResult;
import com.toolplatform.service.ScriptRunnerService.ScriptRunResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Example;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ToolServiceTest {

    private ToolService newService() {
        return new ToolService(null, null, null, null, null, null, false);
    }

    private void put(ZipOutputStream zos, String name, byte[] bytes) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(bytes);
        zos.closeEntry();
    }

    private byte[] zipBytes(String... names) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String n : names) put(zos, n, ("x" + n).getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    @Test
    void extractZipKeepsAllEntriesWithPaths() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "data/input.csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8));
            put(zos, "templates/note.md", "# t".getBytes(StandardCharsets.UTF_8));
            put(zos, "main.py", "print(1)".getBytes(StandardCharsets.UTF_8));
            put(zos, "doc/plan.docx", new byte[]{1, 2, 3});
        }
        ZipExtractResult r = newService().extractZipContent(new ByteArrayInputStream(bos.toByteArray()));
        assertTrue(r.getDataFiles().containsKey("data/input.csv"));
        assertTrue(r.getDataFiles().containsKey("templates/note.md"));
        assertTrue(r.getDataFiles().containsKey("main.py"));
        assertTrue(r.getDataFiles().containsKey("doc/plan.docx"));
        assertEquals(4, r.getDataFiles().size());
    }

    @Test
    void extractZipKeepsTextContentForPreview() throws Exception {
        ZipExtractResult r = newService().extractZipContent(
                new ByteArrayInputStream(zipBytes("a.csv", "b.txt")));
        assertEquals(2, r.getDataFiles().size());
        assertTrue(r.getContent().contains("a.csv"));
    }

    @Test
    void extractZipRejectsBlockedExecutable() {
        assertThrows(java.io.IOException.class, () -> newService().extractZipContent(
                new ByteArrayInputStream(zipBytes("evil.exe"))));
    }

    @Test
    void blockedFileNameCoversAllBlacklist() {
        for (String name : List.of("a.exe", "a/b.dll", "c.bat", "c.cmd", "p.ps1", "m.msi", "x.scr", "c.com", "l.jar")) {
            assertTrue(ToolService.isBlockedFileName(name), name);
        }
        assertFalse(ToolService.isBlockedFileName("doc.txt"));
        assertFalse(ToolService.isBlockedFileName("input.csv"));
        assertFalse(ToolService.isBlockedFileName("noext"));
    }

    @Test
    void applyFormatTemplateReplacesPlaceholder() throws Exception {
        Path p = writeText("# Report\n{{result}}\n");
        assertEquals("# Report\nhello\n", ToolService.applyFormatTemplate(p, "hello"));
    }

    @Test
    void applyFormatTemplateAppendsContentWhenNoPlaceholder() throws Exception {
        Path p = writeText("fix");
        assertEquals("fix\noutput", ToolService.applyFormatTemplate(p, "output"));
    }

    @Test
    void applyFormatTemplateReturnsFormatAloneWhenContentEmpty() throws Exception {
        Path p = writeText("only");
        assertEquals("only", ToolService.applyFormatTemplate(p, "  "));
    }

    @Test
    void applyFormatTemplateSkipsBinaryTemplate() throws Exception {
        Path p = Files.createTempFile("fmt", ".xlsx");
        Files.write(p, new byte[]{0x50, 0x4B, 0x03, 0x04, (byte) 0xC3, 0x28});
        assertNull(ToolService.applyFormatTemplate(p, "output"));
    }

    private Path writeText(String text) throws Exception {
        Path p = Files.createTempFile("fmt", ".txt");
        Files.writeString(p, text, StandardCharsets.UTF_8);
        return p;
    }

    // --- Sandbox integration tests ---

    private Tool makeTool(long id, Path scriptPath) {
        Tool t = new Tool();
        t.setId(id);
        t.setPackageDir("pkg-" + id);
        t.setEntryFile(scriptPath.getFileName().toString());
        t.setRuntime(null);
        return t;
    }

    private com.toolplatform.repository.ToolRepository stubToolRepo(Tool tool) {
        return new com.toolplatform.repository.ToolRepository() {
            public Optional<Tool> findById(Long id) {
                return Optional.ofNullable(id != null && id.equals(tool.getId()) ? tool : null);
            }
            public <S extends Tool> S save(S entity) { return entity; }
            public <S extends Tool> S saveAndFlush(S entity) { return entity; }
            public <S extends Tool> List<S> saveAllAndFlush(Iterable<S> entities) {
                List<S> out = new ArrayList<>();
                entities.forEach(out::add);
                return out;
            }
            public <S extends Tool> List<S> saveAll(Iterable<S> entities) {
                List<S> out = new ArrayList<>();
                entities.forEach(out::add);
                return out;
            }
            public boolean existsById(Long id) { return false; }
            public List<Tool> findAll() { return List.of(); }
            public List<Tool> findAllById(Iterable<Long> ids) { return List.of(); }
            public long count() { return 0; }
            public void deleteById(Long id) {}
            public void delete(Tool entity) {}
            public void deleteAllById(Iterable<? extends Long> ids) {}
            public void deleteAll() {}
            public void deleteAllByIdInBatch(Iterable<Long> ids) {}
            public void deleteInBatch(Iterable<Tool> entities) {}
            public void deleteAll(Iterable<? extends Tool> entities) {}
            public Page<Tool> findAll(Pageable pageable) { return Page.empty(); }
            public <S extends Tool> List<S> findAll(Example<S> example) { return List.of(); }
            public <S extends Tool> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
            public <S extends Tool> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
            public <S extends Tool> long count(Example<S> example) { return 0; }
            public <S extends Tool> boolean exists(Example<S> example) { return false; }
            public Tool getReferenceById(Long id) { return tool; }
            public Tool getById(Long id) { return tool; }
            public Tool getOne(Long id) { return tool; }
            public List<Tool> findByStatus(String s) { return List.of(); }
            public List<Tool> findByCategoryAndStatus(String c, String s) { return List.of(); }
            public long countByCategoryAndStatus(String c, String s) { return 0; }
            public List<Tool> findByAuthorId(Long a) { return List.of(); }
            public Page<Tool> searchMy(Long a, String kw, Pageable p) { return Page.empty(); }
            public List<Tool> searchTools(String q) { return List.of(); }
            public List<Tool> searchByCategory(String c, String q) { return List.of(); }
            public List<Tool> findAll(Sort s) { return List.of(); }
            public void flush() {}
            public void deleteAllInBatch() {}
            public void deleteAllInBatch(Iterable<Tool> es) {}
            public <S extends Tool> Optional<S> findOne(Example<S> e) { return Optional.empty(); }
            public <S extends Tool> long delete(Example<S> e) { return 0; }
            public <S extends Tool> long deleteAll(Example<S> e) { return 0; }
            public <S extends Tool, R> R findBy(Example<S> e, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> q) { return null; }
        };
    }

    /** 反射写入宿主 @Value 字段，供直接 new ToolService（不经 Spring）的测试用 */
    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = ToolService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** updateCallStats 会读 statRepo，用 mock 返回空 Optional 即可 */
    private com.toolplatform.repository.DownloadStatRepository stubStatRepo() {
        return org.mockito.Mockito.mock(com.toolplatform.repository.DownloadStatRepository.class);
    }

    @Test
    void sandboxEnabled_delegatesToSandboxExecution() throws Exception {
        String expected = "sandbox-output";
        SandboxExecutionService fakeSandbox = new SandboxExecutionService(
                null, true, "256m", "1", 120L, "img:latest", "1000:1000", "/launcher") {
            @Override
            public ScriptRunResult run(String interpreter, Path scriptFile, Map<String, byte[]> dataFiles) {
                return ScriptRunResult.of(0, expected, "python");
            }
        };

        Path tmpDir = Files.createTempDirectory("test_pkg");
        Path script = tmpDir.resolve("main.py");
        Files.writeString(script, "print(1)", StandardCharsets.UTF_8);

        ScriptPackageService pkg = new ScriptPackageService(null) {
            @Override public Path resolvePayload(Long toolId) { return tmpDir; }
            @Override public Path resolveVenvPython(Long toolId) { return tmpDir.resolve("venv_python"); }
        };

        Tool tool = makeTool(1L, script);
        ToolService svc = new ToolService(stubToolRepo(tool), stubStatRepo(), null, null, pkg, fakeSandbox, true);
        setField(svc, "resultDir", Files.createTempDirectory("results").toString());
        FileProcessResult result = svc.processUploadedFiles(1L, 1L,
                new MultipartFile[]{
                    new org.springframework.mock.web.MockMultipartFile("data", "hi.txt", "text/plain", "hello".getBytes())
                });

        assertTrue(result.isPythonExecuted());
        assertEquals(expected, result.getPythonOutput());
    }

    @Test
    void sandboxDisabled_usesScriptRunner() throws Exception {
        String expected = "direct-output";
        SandboxExecutionService fakeSandbox = new SandboxExecutionService(
                null, false, "256m", "1", 120L, "img:latest", "1000:1000", "/launcher") {
            @Override
            public ScriptRunResult run(String interpreter, Path scriptFile, Map<String, byte[]> dataFiles) {
                fail("sandbox should not be invoked when disabled");
                return null;
            }
        };

        Path tmpDir = Files.createTempDirectory("test_pkg");
        Path script = tmpDir.resolve("main.py");
        Files.writeString(script, "print(1)", StandardCharsets.UTF_8);

        ScriptPackageService pkg = new ScriptPackageService(null) {
            @Override public Path resolvePayload(Long toolId) { return tmpDir; }
            @Override public Path resolveVenvPython(Long toolId) { return tmpDir.resolve("venv_python"); }
        };

        com.toolplatform.service.ScriptRunnerService scriptRunner = new com.toolplatform.service.ScriptRunnerService(null, List.of()) {
            @Override
            public ScriptRunResult run(String interpreter, Path scriptFile2, Map<String, byte[]> dataFiles2) {
                return ScriptRunResult.of(0, expected, "python");
            }
        };

        Tool tool = makeTool(2L, script);
        ToolService svc = new ToolService(stubToolRepo(tool), stubStatRepo(), null, scriptRunner, pkg, fakeSandbox, false);
        setField(svc, "resultDir", Files.createTempDirectory("results").toString());
        FileProcessResult result = svc.processUploadedFiles(2L, 1L,
                new MultipartFile[]{
                    new org.springframework.mock.web.MockMultipartFile("data", "hi.txt", "text/plain", "hello".getBytes())
                });

        assertTrue(result.isPythonExecuted());
        assertEquals(expected, result.getPythonOutput());
    }
}
