package com.toolplatform.service;

import com.toolplatform.service.ToolService.ZipExtractResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ToolServiceTest {

    private ToolService newService() {
        return new ToolService(null, null, null, null, null);
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
}