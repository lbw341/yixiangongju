package com.toolplatform.service;

import com.toolplatform.entity.*;
import com.toolplatform.repository.*;
import com.toolplatform.service.ScriptRunnerService.ScriptRunResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 工具服务层
 * 处理文件处理、Python执行、周报生成等核心业务逻辑
 */
@Service
public class ToolService {

    @Value("${upload.template-dir}")
    private String templateDir;

    @Value("${upload.result-dir}")
    private String resultDir;

    private static final Set<String> TEXT_EXT = Set.of("txt", "md", "log", "py", "sh", "bat", "ps1", "json", "csv");
    private static final Set<String> EXCEL_EXT = Set.of("xlsx", "xls");
    private static final Set<String> BLOCKED_EXT = Set.of("exe", "dll", "bat", "cmd", "ps1", "msi", "scr", "com", "jar");
    private static final int MAX_ZIP_ENTRIES = 500;
    private static final long MAX_ZIP_BYTES = 200L * 1024 * 1024;

    private final ToolRepository toolRepo;
    private final DownloadStatRepository statRepo;
    private final UserToolUsageRepository usageRepo;
    private final ScriptRunnerService scriptRunner;
    private final ScriptPackageService scriptPackageService;

    public ToolService(ToolRepository toolRepo, DownloadStatRepository statRepo, UserToolUsageRepository usageRepo,
                       ScriptRunnerService scriptRunner, ScriptPackageService scriptPackageService) {
        this.toolRepo = toolRepo;
        this.statRepo = statRepo;
        this.usageRepo = usageRepo;
        this.scriptRunner = scriptRunner;
        this.scriptPackageService = scriptPackageService;
    }

    /**
     * 更新工具下载统计
     */
    public void updateDownloadStats(Long toolId, Long userId) {
        Tool t = toolRepo.findById(toolId).orElse(null);
        if (t == null) return;

        t.setDownloads(t.getDownloads() + 1);
        toolRepo.save(t);

        String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        var stat = statRepo.findByToolIdAndDate(toolId, today);
        if (stat.isPresent()) {
            stat.get().setCount(stat.get().getCount() + 1);
            statRepo.save(stat.get());
        } else {
            DownloadStat ds = new DownloadStat();
            ds.setToolId(toolId);
            ds.setDate(today);
            ds.setCount(1);
            statRepo.save(ds);
        }

        var usage = usageRepo.findByUserIdAndToolId(userId, toolId);
        if (usage.isPresent()) {
            usage.get().setUseCount(usage.get().getUseCount() + 1);
            usage.get().setLastUsed(LocalDateTime.now().toString());
            usageRepo.save(usage.get());
        } else {
            UserToolUsage utu = new UserToolUsage();
            utu.setUserId(userId);
            utu.setToolId(toolId);
            utu.setLastUsed(LocalDateTime.now().toString());
            utu.setUseCount(1);
            usageRepo.save(utu);
        }
    }

    /**
     * 更新工具调用统计
     */
    public void updateCallStats(Long toolId) {
        Tool t = toolRepo.findById(toolId).orElse(null);
        if (t == null) return;

        t.setCalls(t.getCalls() + 1);
        toolRepo.save(t);

        String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        var stat = statRepo.findByToolIdAndDate(toolId, today);
        if (stat.isPresent()) {
            stat.get().setCount(stat.get().getCount() + 1);
            statRepo.save(stat.get());
        } else {
            DownloadStat ds = new DownloadStat();
            ds.setToolId(toolId);
            ds.setDate(today);
            ds.setCount(1);
            statRepo.save(ds);
        }
    }

    /**
     * 读取Excel文件内容
     */
    public String readExcelContent(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        WorkbookWrapper workbook = null;

        try {
            if (inputStream.markSupported()) {
                inputStream.mark(8192);
            }
            byte[] header = new byte[8];
            inputStream.read(header);
            boolean isXlsx = header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;

            if (inputStream.markSupported()) {
                inputStream.reset();
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(header);
                byte[] buf = new byte[8192];
                int len;
                while ((len = inputStream.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
                inputStream = new ByteArrayInputStream(baos.toByteArray());
            }

            workbook = new WorkbookWrapper(inputStream, isXlsx);

            for (RowWrapper row : workbook.getRows()) {
                List<String> rowData = new ArrayList<>();
                for (String cellValue : row.getValues()) {
                    rowData.add(cellValue);
                }
                content.append(String.join(" | ", rowData)).append("\n");
            }
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }

        return content.toString();
    }

    /**
     * 解压ZIP文件并提取内容
     */
    public ZipExtractResult extractZipContent(InputStream inputStream) throws IOException {
        ZipExtractResult result = new ZipExtractResult();
        byte[] zipData = inputStream.readAllBytes();

        Path tempZipFile = Files.createTempFile("upload_", ".zip");
        Files.write(tempZipFile, zipData);

        List<Charset> charsets = Arrays.asList(
                StandardCharsets.UTF_8,
                Charset.forName("GBK"),
                Charset.forName("GB18030"),
                StandardCharsets.ISO_8859_1,
                Charset.defaultCharset()
        );

        boolean success = false;
        Exception lastError = null;

        for (Charset charset : charsets) {
            try (ZipFile zipFile = new ZipFile(tempZipFile.toFile(), charset)) {
                StringBuilder tempContent = new StringBuilder();
                List<String> tempFiles = new ArrayList<>();
                Map<String, String> tempPyFiles = new LinkedHashMap<>();
                Map<String, byte[]> tempDataFiles = new LinkedHashMap<>();

                int count = 0;
                long totalBytes = 0;
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    count++;
                    if (count > MAX_ZIP_ENTRIES) {
                        throw new IOException("包内文件数超过上限(" + MAX_ZIP_ENTRIES + ")");
                    }

                    String entryName = entry.getName();
                    if (isBlockedFileName(entryName)) {
                        throw new IOException("不允许的可执行文件: " + entryName);
                    }

                    byte[] bytes;
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        bytes = is.readAllBytes();
                    }
                    totalBytes += bytes.length;
                    if (totalBytes > MAX_ZIP_BYTES) {
                        throw new IOException("解压后总大小超过上限(" + (MAX_ZIP_BYTES / 1024 / 1024) + "MB)");
                    }

                    tempDataFiles.put(entryName, bytes);

                    String ext = extOf(entryName);
                    if ("py".equalsIgnoreCase(ext)) {
                        tempPyFiles.put(entryName, new String(bytes, StandardCharsets.UTF_8));
                        tempFiles.add(entryName);
                    } else if (TEXT_EXT.contains(ext)) {
                        tempContent.append("===== 文件: ").append(entryName).append(" =====\n");
                        tempContent.append(decodeTextContent(bytes));
                        tempContent.append("\n\n");
                        tempFiles.add(entryName);
                    } else if (EXCEL_EXT.contains(ext)) {
                        tempContent.append("===== Excel文件: ").append(entryName).append(" =====\n");
                        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                        tempContent.append(readExcelContent(bais));
                        tempContent.append("\n\n");
                        tempFiles.add(entryName);
                    }
                }

                result.setContent(tempContent.toString());
                result.setProcessedFiles(tempFiles);
                result.setPythonFiles(tempPyFiles);
                result.setDataFiles(tempDataFiles);
                success = true;
                break;
            } catch (Exception e) {
                if (e instanceof IOException && e.getMessage() != null
                        && (e.getMessage().contains("不允许") || e.getMessage().contains("超过上限"))) {
                    throw new IOException(e.getMessage());
                }
                lastError = e;
            }
        }

        Files.deleteIfExists(tempZipFile);

        if (!success) {
            throw new IOException("无法解析ZIP文件: " + (lastError != null ? lastError.getMessage() : "未知错误"));
        }

        return result;
    }

    /**
     * 处理上传的文件
     * 优先执行工具的脚本模板，其次生成通用周报；结果套用格式模板输出
     */
    public FileProcessResult processUploadedFiles(Long toolId, Long userId, MultipartFile[] files) throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String resultName = "result_" + toolId + "_" + userId + "_" + timestamp + ".txt";
        Path resultPath = Paths.get(resultDir, resultName);

        updateCallStats(toolId);

        Tool tool = toolRepo.findById(toolId).orElse(null);

        StringBuilder allContent = new StringBuilder();
        List<String> processedFiles = new ArrayList<>();
        Map<String, byte[]> allDataFiles = new LinkedHashMap<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (isBlockedFileName(originalName)) {
                throw new IOException("不允许上传的可执行文件: " + originalName);
            }

            String ext = extOf(originalName);

            if ("zip".equalsIgnoreCase(ext)) {
                ZipExtractResult zipResult = extractZipContent(file.getInputStream());
                allContent.append(zipResult.getContent());
                processedFiles.addAll(zipResult.getProcessedFiles());
                allDataFiles.putAll(zipResult.getDataFiles());
            } else if ("xlsx".equalsIgnoreCase(ext) || "xls".equalsIgnoreCase(ext)) {
                byte[] fileBytes = file.getInputStream().readAllBytes();
                String excelContent = readExcelContent(new ByteArrayInputStream(fileBytes));
                allContent.append("===== Excel文件: ").append(originalName).append(" =====\n");
                allContent.append(excelContent).append("\n\n");
                processedFiles.add(originalName);
                allDataFiles.put(originalName, fileBytes);
            } else if (TEXT_EXT.contains(ext)) {
                byte[] fileBytes = file.getInputStream().readAllBytes();
                String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
                allContent.append("===== 文件: ").append(originalName).append(" =====\n");
                allContent.append(fileContent).append("\n\n");
                processedFiles.add(originalName);
                allDataFiles.put(originalName, fileBytes);
            } else {
                byte[] fileBytes = file.getInputStream().readAllBytes();
                processedFiles.add(originalName);
                allDataFiles.put(originalName, fileBytes);
            }
        }

        String resultContent = "";

        String scriptFile = null;
        String formatFile = null;
        if (tool != null) {
            String tf = tool.getTemplateFile();
            String ff = tool.getFormatTemplate();
            if (tf != null && tf.toLowerCase().endsWith(".py")) {
                scriptFile = tf;
                formatFile = (ff != null && !ff.isEmpty()) ? ff : null;
            } else {
                formatFile = (ff != null && !ff.isEmpty()) ? ff : ((tf != null && !tf.isEmpty()) ? tf : null);
            }
        }

        boolean scriptExecuted = false;
        if (tool != null && tool.getPackageDir() != null && !tool.getPackageDir().isEmpty()
                && Files.exists(scriptPackageService.resolvePayload(tool.getId()).resolve(tool.getEntryFile()))) {
            Path scriptPath = scriptPackageService.resolvePayload(tool.getId()).resolve(tool.getEntryFile());
            try {
                String runtime = tool.getRuntime();
                if (runtime == null || "python".equals(runtime)) {
                    Path venvPy = scriptPackageService.resolveVenvPython(tool.getId());
                    String py = Files.exists(venvPy) ? venvPy.toAbsolutePath().toString() : null;
                    resultContent = runScriptTemplatePython(py, scriptPath, allDataFiles);
                } else {
                    resultContent = runScriptTemplateByRuntime(runtime, scriptPath, allDataFiles);
                }
                scriptExecuted = true;
            } catch (Exception e) {
                resultContent = "脚本执行失败: " + e.getMessage();
                scriptExecuted = true;
            }
        } else if (scriptFile != null && Files.exists(getTemplatePath(scriptFile))) {
            try {
                resultContent = runScriptTemplate(null, getTemplatePath(scriptFile), allDataFiles);
                scriptExecuted = true;
            } catch (Exception e) {
                resultContent = "脚本执行失败: " + e.getMessage();
                scriptExecuted = true;
            }
        } else if (allContent.length() > 0) {
            resultContent = generateWeeklyReport(allContent.toString());
        }

        boolean isPackage = tool != null && tool.getPackageDir() != null && !tool.getPackageDir().isEmpty();
        if (!isPackage && formatFile != null && Files.exists(getTemplatePath(formatFile))) {
            String formatted = applyFormatTemplate(getTemplatePath(formatFile), resultContent);
            if (formatted != null) {
                resultContent = formatted;
            }
        }

        FileProcessResult result = new FileProcessResult();
        result.setResultName(resultName);
        result.setProcessedFiles(processedFiles);
        if (scriptExecuted) {
            result.setPythonOutput(resultContent);
            result.setPythonExecuted(true);
        }

        if (!resultContent.trim().isEmpty()) {
            Files.writeString(resultPath, resultContent, StandardCharsets.UTF_8);
        }

        return result;
    }

    /**
     * 运行工具的脚本模板并组装用户可见的结果文本
     * 约定: sys.argv[1] 恒为数据目录，sys.argv[2:] 为数据文件列表（可为空）
     * pythonCmd 为 null 时走 ScriptRunnerService 的自动探测
     */
    private String runScriptTemplate(String pythonCmd, Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        ScriptRunResult r = scriptRunner.run(pythonCmd, scriptFile, dataFiles);
        if (!r.isPythonFound()) {
            return "错误: 服务端未安装Python或Python未添加到环境变量";
        }
        if (r.isTimedOut()) {
            return "执行超时 (超过120秒)\n" + r.getOutput();
        }
        if (r.getExitCode() != 0) {
            return "执行错误 (Exit code: " + r.getExitCode() + ")\n" + r.getOutput();
        }
        return r.getOutput();
    }

    private String runScriptTemplatePython(String interpreter, Path scriptFile, Map<String, byte[]> dataFiles)
            throws IOException, InterruptedException {
        ScriptRunResult r = scriptRunner.run(interpreter, scriptFile, dataFiles);
        if (!r.isPythonFound()) return "错误: 服务端未安装Python或Python未添加到环境变量";
        return toResultText(r, "python");
    }

    private String runScriptTemplateByRuntime(String runtime, Path scriptFile, Map<String, byte[]> dataFiles)
            throws IOException, InterruptedException {
        ScriptRunResult r = scriptRunner.runBy(runtime, scriptFile, dataFiles);
        if (!r.isPythonFound()) return "错误: 对应运行时未安装 (runtime=" + r.getRuntime() + ")";
        return toResultText(r, runtime);
    }

    private String toResultText(ScriptRunResult r, String runtime) {
        if (r.isTimedOut()) return "执行超时 (超过" + ("java".equals(runtime) ? 180 : 120) + "秒)\n" + r.getOutput();
        if (r.getExitCode() != 0) return "执行错误 (Exit code: " + r.getExitCode() + ")\n" + r.getOutput();
        return r.getOutput();
    }

    /**
     * 生成周报
     */
    public String generateWeeklyReport(String content) {
        StringBuilder report = new StringBuilder();
        report.append("============================\n");
        report.append("      项目周报\n");
        report.append("============================\n");
        report.append("\n");
        report.append("【报告周期】").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))).append("\n");
        report.append("\n");
        report.append("【本周工作内容】\n");
        report.append("----------------\n");

        String[] lines = content.split("\n");
        boolean isExcelFormat = false;
        List<String> headers = new ArrayList<>();
        List<List<String>> tableData = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                if (line.contains(" | ")) {
                    isExcelFormat = true;
                    String[] cells = line.split(" \\| ");
                    if (i == 0 && cells.length > 1) {
                        headers = Arrays.asList(cells);
                    } else {
                        tableData.add(Arrays.asList(cells));
                    }
                } else {
                    if (line.startsWith("- ") || line.startsWith("* ")) {
                        report.append((i + 1)).append(". ").append(line.substring(2)).append("\n");
                    } else {
                        report.append((i + 1)).append(". ").append(line).append("\n");
                    }
                }
            }
        }

        if (isExcelFormat && !tableData.isEmpty()) {
            if (!headers.isEmpty()) {
                report.append("表格数据（").append(headers.size()).append("列）：\n");
                report.append("表头: ").append(String.join(", ", headers)).append("\n");
                report.append("----------------\n");
            }
            int rowNum = 1;
            for (List<String> row : tableData) {
                report.append(rowNum).append(". ").append(String.join(" | ", row)).append("\n");
                rowNum++;
            }
        }

        int taskCount = isExcelFormat ? tableData.size() : lines.length;

        report.append("\n");
        report.append("【工作成果总结】\n");
        report.append("----------------\n");
        report.append("本周共完成 ").append(taskCount).append(" 项工作任务。\n");
        report.append("\n");
        report.append("【下周工作计划】\n");
        report.append("----------------\n");
        report.append("1. 继续推进当前项目进度\n");
        report.append("2. 完成待办事项\n");
        report.append("3. 与团队成员沟通协作\n");
        report.append("\n");
        report.append("【备注】\n");
        report.append("----------------\n");
        report.append("无\n");
        report.append("\n");
        report.append("============================\n");
        report.append("                              \n");
        report.append("                              \n");
        report.append("                              \n");
        report.append("报告人：\n");
        report.append("日期：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))).append("\n");

        return report.toString();
    }

    /**
     * 保存模板文件
     */
    public String saveTemplateFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String uniqueName = System.currentTimeMillis() + "_" + fileName;
        Path uploadPath = Paths.get(templateDir, uniqueName);
        Files.createDirectories(uploadPath.getParent());
        Files.copy(file.getInputStream(), uploadPath, StandardCopyOption.REPLACE_EXISTING);
        return uniqueName;
    }

    /**
     * 获取模板文件路径
     */
    public Path getTemplatePath(String templateFile) {
        return Paths.get(templateDir, templateFile);
    }

    /**
     * 预览模板文件内容：按扩展名分类返回 JSON。
     * 文本类 → content 字段（UTF-8 字符串）；
     * Excel 类 → content 字段（HTML 表格片段）；
     * 其他二进制 → 抛异常让前端提示"建议下载查看"。
     */
    public Map<String, Object> previewTemplate(Path filePath) throws IOException {
        String name = filePath.getFileName().toString();
        String ext = extOf(name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("filename", name);
        result.put("extension", ext);
        result.put("size", Files.size(filePath));

        if (Set.of("xlsx", "xls").contains(ext)) {
            String html = excelToHtmlTable(filePath);
            result.put("type", "html");
            result.put("content", html);
        } else if (Set.of("png", "jpg", "jpeg", "gif", "svg", "webp").contains(ext)) {
            result.put("type", "binary");
            result.put("content", "");
            result.put("message", "图片类型，建议下载查看");
        } else if (Set.of("docx", "pdf", "pptx", "zip", "rar", "7z", "jar").contains(ext)) {
            result.put("type", "binary");
            result.put("content", "");
            result.put("message", "该类型为二进制文件，请点击「下载模板」查看");
        } else {
            // 全部按 UTF-8 文本处理
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            result.put("type", "text");
            result.put("content", content);
            // 推断语言
            String lang = ext;
            Map<String, String> langMap = new LinkedHashMap<>();
            langMap.put("py", "python"); langMap.put("sh", "bash"); langMap.put("js", "javascript");
            langMap.put("ts", "typescript"); langMap.put("java", "java"); langMap.put("go", "go");
            langMap.put("c", "c"); langMap.put("cpp", "cpp"); langMap.put("css", "css");
            langMap.put("html", "html"); langMap.put("json", "json"); langMap.put("yaml", "yaml");
            langMap.put("yml", "yaml"); langMap.put("md", "markdown"); langMap.put("xml", "xml");
            langMap.put("sql", "sql"); langMap.put("txt", "text"); langMap.put("csv", "text");
            result.put("language", langMap.getOrDefault(lang, lang));
        }
        return result;
    }

    /**
     * 把 Excel 第一个工作表转成 HTML <table> 片段（不含 <html>/<body>，方便嵌入前端 Modal）。
     */
    private String excelToHtmlTable(Path filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"excel-preview\">");
        sb.append("<table class=\"excel-table\" border=\"1\" cellspacing=\"0\" cellpadding=\"4\">");
        boolean isFirst = true;
        try (WorkbookWrapper ww = new WorkbookWrapper(Files.newInputStream(filePath), filePath.toString().toLowerCase().endsWith(".xlsx"))) {
            for (RowWrapper row : ww.getRows()) {
                List<String> cells = row.getValues();
                sb.append("<tr>");
                for (String val : cells) {
                    sb.append(isFirst ? "<th>" : "<td>");
                    sb.append(escapeHtml(val == null ? "" : val));
                    sb.append(isFirst ? "</th>" : "</td>");
                }
                sb.append("</tr>");
                isFirst = false;
            }
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    static boolean isBlockedFileName(String name) {
        return BLOCKED_EXT.contains(extOf(name));
    }

    /**
     * 应用格式模板：将 {{result}} 占位符替换为脚本输出。
     * 二进制(非UTF-8文本)模板直接返回 null，由调用方跳过拼接，避免崩溃。
     */
    static String applyFormatTemplate(Path formatFile, String resultContent) throws IOException {
        String format;
        try {
            format = Files.readString(formatFile, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return null;
        }
        if (format.contains("{{result}}")) {
            return format.replace("{{result}}", resultContent);
        } else if (!resultContent.trim().isEmpty()) {
            return format + "\n" + resultContent;
        }
        return format;
    }

    private String decodeTextContent(byte[] bytes) {
        return decodeOutput(bytes);
    }

    private String decodeOutput(byte[] bytes) {
        if (bytes.length == 0) return "";

        String utf8 = decodeStrict(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) return utf8;

        String gbk = decodeStrict(bytes, Charset.forName("GBK"));
        if (gbk != null) return gbk;

        return new String(bytes, Charset.forName("GB18030"));
    }

    private String decodeStrict(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * ZIP文件提取结果
     */
    public static class ZipExtractResult {
        private String content;
        private List<String> processedFiles;
        private Map<String, String> pythonFiles;
        private Map<String, byte[]> dataFiles;

        public ZipExtractResult() {
            this.content = "";
            this.processedFiles = new ArrayList<>();
            this.pythonFiles = new LinkedHashMap<>();
            this.dataFiles = new LinkedHashMap<>();
        }

        // Getters and Setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getProcessedFiles() { return processedFiles; }
        public void setProcessedFiles(List<String> processedFiles) { this.processedFiles = processedFiles; }
        public Map<String, String> getPythonFiles() { return pythonFiles; }
        public void setPythonFiles(Map<String, String> pythonFiles) { this.pythonFiles = pythonFiles; }
        public Map<String, byte[]> getDataFiles() { return dataFiles; }
        public void setDataFiles(Map<String, byte[]> dataFiles) { this.dataFiles = dataFiles; }
    }

    /**
     * 文件处理结果
     */
    public static class FileProcessResult {
        private String resultName;
        private List<String> processedFiles;
        private String pythonOutput;
        private boolean pythonExecuted;

        public String getResultName() { return resultName; }
        public void setResultName(String resultName) { this.resultName = resultName; }
        public List<String> getProcessedFiles() { return processedFiles; }
        public void setProcessedFiles(List<String> processedFiles) { this.processedFiles = processedFiles; }
        public String getPythonOutput() { return pythonOutput; }
        public void setPythonOutput(String pythonOutput) { this.pythonOutput = pythonOutput; }
        public boolean isPythonExecuted() { return pythonExecuted; }
        public void setPythonExecuted(boolean pythonExecuted) { this.pythonExecuted = pythonExecuted; }
    }

    /**
     * Excel Workbook包装类（简化导入）
     */
    private static class WorkbookWrapper implements AutoCloseable {
        private final Object workbook;

        public WorkbookWrapper(InputStream inputStream, boolean isXlsx) throws IOException {
            org.apache.poi.ss.usermodel.Workbook wb;
            if (isXlsx) {
                wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(inputStream);
            } else {
                wb = new org.apache.poi.hssf.usermodel.HSSFWorkbook(inputStream);
            }
            this.workbook = wb;
        }

        public List<RowWrapper> getRows() {
            org.apache.poi.ss.usermodel.Workbook wb = (org.apache.poi.ss.usermodel.Workbook) workbook;
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            List<RowWrapper> rows = new ArrayList<>();
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                rows.add(new RowWrapper(row));
            }
            return rows;
        }

        @Override
        public void close() throws IOException {
            if (workbook instanceof org.apache.poi.ss.usermodel.Workbook) {
                ((org.apache.poi.ss.usermodel.Workbook) workbook).close();
            }
        }
    }

    /**
     * Excel Row包装类
     */
    private static class RowWrapper {
        private final org.apache.poi.ss.usermodel.Row row;

        public RowWrapper(org.apache.poi.ss.usermodel.Row row) {
            this.row = row;
        }

        public List<String> getValues() {
            List<String> values = new ArrayList<>();
            for (org.apache.poi.ss.usermodel.Cell cell : row) {
                String cellValue = "";
                switch (cell.getCellType()) {
                    case STRING:
                        cellValue = cell.getStringCellValue();
                        break;
                    case NUMERIC:
                        if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                            cellValue = cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        } else {
                            cellValue = String.valueOf((long) cell.getNumericCellValue());
                        }
                        break;
                    case BOOLEAN:
                        cellValue = String.valueOf(cell.getBooleanCellValue());
                        break;
                    case FORMULA:
                        try {
                            cellValue = cell.getStringCellValue();
                        } catch (Exception e) {
                            cellValue = String.valueOf(cell.getNumericCellValue());
                        }
                        break;
                    default:
                        cellValue = "";
                }
                values.add(cellValue);
            }
            return values;
        }
    }
}