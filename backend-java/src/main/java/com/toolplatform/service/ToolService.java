package com.toolplatform.service;

import com.toolplatform.entity.*;
import com.toolplatform.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
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

    private static final Set<String> ALLOWED_EXT = Set.of("xlsx", "csv", "json", "zip", "py", "sh", "bat", "ps1", "txt", "xls");
    private static final Set<String> TEXT_EXT = Set.of("txt", "md", "log", "py", "sh", "bat", "ps1", "json", "csv");
    private static final Set<String> EXCEL_EXT = Set.of("xlsx", "xls");

    private final ToolRepository toolRepo;
    private final DownloadStatRepository statRepo;
    private final UserToolUsageRepository usageRepo;

    public ToolService(ToolRepository toolRepo, DownloadStatRepository statRepo, UserToolUsageRepository usageRepo) {
        this.toolRepo = toolRepo;
        this.statRepo = statRepo;
        this.usageRepo = usageRepo;
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
     * 执行Python代码
     */
    public String executePythonCode(String code, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("python_work_");
        Path scriptFile = workDir.resolve("script.py");

        String preamble = "# -*- coding: utf-8 -*-\n";
        Files.writeString(scriptFile, preamble + code, StandardCharsets.UTF_8);

        Path dataDir = workDir.resolve("data");
        List<String> dataFilePaths = new ArrayList<>();

        if (dataFiles != null && !dataFiles.isEmpty()) {
            Files.createDirectories(dataDir);
            for (Map.Entry<String, byte[]> entry : dataFiles.entrySet()) {
                String originalName = entry.getKey();
                int lastSlash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
                String fileName = lastSlash >= 0 ? originalName.substring(lastSlash + 1) : originalName;
                if (fileName.contains("/") || fileName.contains("\\") || fileName.contains(":")) {
                    fileName = fileName.replaceAll("[/:*?\"<>|]", "_");
                }
                Path dataFile = dataDir.resolve(fileName);
                Files.write(dataFile, entry.getValue());
                dataFilePaths.add(dataFile.toString());
            }
        }

        String pythonCmd = findPythonCommand();
        if (pythonCmd == null) {
            deleteDirectory(workDir.toFile());
            return "错误: 服务端未安装Python或Python未添加到环境变量";
        }

        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
        command.add("-u");
        command.add(scriptFile.toString());

        if (!dataFilePaths.isEmpty()) {
            command.add(dataDir.toString());
            command.addAll(dataFilePaths);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(workDir.toFile());

        Map<String, String> env = pb.environment();
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONUTF8", "1");

        Process process = pb.start();

        if (!dataFilePaths.isEmpty()) {
            try (OutputStream os = process.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                writer.write(dataDir.toString());
                writer.newLine();
                for (String path : dataFilePaths) {
                    writer.write(path);
                    writer.newLine();
                }
                writer.flush();
            }
        }

        StringBuilder output = new StringBuilder();
        InputStream rawStream = process.getInputStream();

        Thread readerThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(rawStream, StandardCharsets.UTF_8));
                String line;
                int lineCount = 0;
                long charCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 5000 && charCount < 1000000) {
                    output.append(line).append("\n");
                    lineCount++;
                    charCount += line.length();
                }
                if (lineCount >= 5000) {
                    output.append("\n[输出行数过多，已截断]\n");
                } else if (charCount >= 1000000) {
                    output.append("\n[输出内容过大，已截断]\n");
                }
            } catch (IOException e) {}
        });
        readerThread.start();

        boolean completed = process.waitFor(120, TimeUnit.SECONDS);
        readerThread.join(2000);

        if (!completed) {
            process.destroyForcibly();
            deleteDirectory(workDir.toFile());
            return "执行超时 (超过120秒)\n" + output.toString();
        }

        int exitCode = process.exitValue();
        deleteDirectory(workDir.toFile());

        if (exitCode != 0) {
            return "执行错误 (Exit code: " + exitCode + ")\n" + output.toString();
        }
        return output.toString();
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

                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;

                    String entryName = entry.getName();
                    String ext = entryName.contains(".") ? entryName.substring(entryName.lastIndexOf('.') + 1).toLowerCase() : "";

                    byte[] bytes;
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        bytes = is.readAllBytes();
                    }

                    if ("py".equalsIgnoreCase(ext)) {
                        String pyCode = new String(bytes, StandardCharsets.UTF_8);
                        tempPyFiles.put(entryName, pyCode);
                        tempFiles.add(entryName);
                    } else if (TEXT_EXT.contains(ext)) {
                        tempContent.append("===== 文件: ").append(entryName).append(" =====\n");
                        tempContent.append(decodeTextContent(bytes));
                        tempContent.append("\n\n");
                        tempFiles.add(entryName);
                        tempDataFiles.put(entryName, bytes);
                    } else if (EXCEL_EXT.contains(ext)) {
                        tempContent.append("===== Excel文件: ").append(entryName).append(" =====\n");
                        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                        tempContent.append(readExcelContent(bais));
                        tempContent.append("\n\n");
                        tempFiles.add(entryName);
                        tempDataFiles.put(entryName, bytes);
                    }
                }

                result.setContent(tempContent.toString());
                result.setProcessedFiles(tempFiles);
                result.setPythonFiles(tempPyFiles);
                result.setDataFiles(tempDataFiles);
                success = true;
                break;
            } catch (Exception e) {
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

            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase() : "";

            if (!ALLOWED_EXT.contains(ext)) {
                continue;
            }

            if ("zip".equalsIgnoreCase(ext)) {
                ZipExtractResult zipResult = extractZipContent(file.getInputStream());
                allContent.append(zipResult.getContent());
                processedFiles.addAll(zipResult.getProcessedFiles());
                allDataFiles.putAll(zipResult.getDataFiles());
                for (Map.Entry<String, String> py : zipResult.getPythonFiles().entrySet()) {
                    allDataFiles.put(py.getKey(), py.getValue().getBytes(StandardCharsets.UTF_8));
                }
            } else if ("xlsx".equalsIgnoreCase(ext) || "xls".equalsIgnoreCase(ext)) {
                byte[] fileBytes = file.getInputStream().readAllBytes();
                String excelContent = readExcelContent(new ByteArrayInputStream(fileBytes));
                allContent.append("===== Excel文件: ").append(originalName).append(" =====\n");
                allContent.append(excelContent).append("\n\n");
                processedFiles.add(originalName);
                allDataFiles.put(originalName, fileBytes);
            } else {
                byte[] fileBytes = file.getInputStream().readAllBytes();
                String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
                allContent.append("===== 文件: ").append(originalName).append(" =====\n");
                allContent.append(fileContent).append("\n\n");
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
        if (scriptFile != null && Files.exists(getTemplatePath(scriptFile))) {
            try {
                resultContent = runScriptTemplate(getTemplatePath(scriptFile), allDataFiles);
                scriptExecuted = true;
            } catch (Exception e) {
                resultContent = "脚本执行失败: " + e.getMessage();
                scriptExecuted = true;
            }
        } else if (allContent.length() > 0) {
            resultContent = generateWeeklyReport(allContent.toString());
        }

        if (formatFile != null && Files.exists(getTemplatePath(formatFile))) {
            String format = Files.readString(getTemplatePath(formatFile), StandardCharsets.UTF_8);
            if (format.contains("{{result}}")) {
                resultContent = format.replace("{{result}}", resultContent);
            } else if (!resultContent.trim().isEmpty()) {
                resultContent = format + "\n" + resultContent;
            } else {
                resultContent = format;
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
     * 运行工具的脚本模板
     * 约定: python -u <script> <数据目录> <文件1> <文件2> ...
     */
    private String runScriptTemplate(Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("tool_script_");
        Path dataDir = workDir.resolve("data");
        List<String> dataFilePaths = new ArrayList<>();

        if (dataFiles != null && !dataFiles.isEmpty()) {
            Files.createDirectories(dataDir);
            for (Map.Entry<String, byte[]> entry : dataFiles.entrySet()) {
                String originalName = entry.getKey();
                int lastSlash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
                String fileName = lastSlash >= 0 ? originalName.substring(lastSlash + 1) : originalName;
                if (fileName.contains("/") || fileName.contains("\\") || fileName.contains(":")) {
                    fileName = fileName.replaceAll("[/:*?\"<>|]", "_");
                }
                Path dataFile = dataDir.resolve(fileName);
                Files.write(dataFile, entry.getValue());
                dataFilePaths.add(dataFile.toString());
            }
        }

        String pythonCmd = findPythonCommand();
        if (pythonCmd == null) {
            deleteDirectory(workDir.toFile());
            return "错误: 服务端未安装Python或Python未添加到环境变量";
        }

        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
        command.add("-u");
        command.add(scriptFile.toString());
        if (!dataFilePaths.isEmpty()) {
            command.add(dataDir.toString());
            command.addAll(dataFilePaths);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(workDir.toFile());

        Map<String, String> env = pb.environment();
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONUTF8", "1");

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        InputStream rawStream = process.getInputStream();

        Thread readerThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(rawStream, StandardCharsets.UTF_8));
                String line;
                int lineCount = 0;
                long charCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 5000 && charCount < 1000000) {
                    output.append(line).append("\n");
                    lineCount++;
                    charCount += line.length();
                }
                if (lineCount >= 5000) {
                    output.append("\n[输出行数过多，已截断]\n");
                } else if (charCount >= 1000000) {
                    output.append("\n[输出内容过大，已截断]\n");
                }
            } catch (IOException e) {}
        });
        readerThread.start();

        boolean completed = process.waitFor(120, TimeUnit.SECONDS);
        readerThread.join(2000);

        if (!completed) {
            process.destroyForcibly();
            deleteDirectory(workDir.toFile());
            return "执行超时 (超过120秒)\n" + output.toString();
        }

        int exitCode = process.exitValue();
        deleteDirectory(workDir.toFile());

        if (exitCode != 0) {
            return "执行错误 (Exit code: " + exitCode + ")\n" + output.toString();
        }
        return output.toString();
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

    private String findPythonCommand() {
        String[] candidates = {"python", "python3", "py"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                Process p = pb.start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return cmd;
                }
            } catch (Exception e) {}
        }

        String[] paths = {
                "C:\\Python39\\python.exe",
                "C:\\Python310\\python.exe",
                "C:\\Python311\\python.exe",
                "C:\\Python312\\python.exe",
                "C:\\Program Files\\Python39\\python.exe",
                "C:\\Program Files\\Python310\\python.exe",
                "C:\\Program Files\\Python311\\python.exe",
                "C:\\Program Files\\Python312\\python.exe",
                "C:\\Program Files (x86)\\Python39\\python.exe",
                "C:\\Program Files (x86)\\Python310\\python.exe",
                "C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python311\\python.exe",
                "C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python312\\python.exe"
        };
        for (String path : paths) {
            if (new File(path).exists()) {
                return path;
            }
        }

        return null;
    }

    private String decodeTextContent(byte[] bytes) {
        return decodeOutput(bytes);
    }

    private String decodeOutput(byte[] bytes) {
        if (bytes.length == 0) return "";

        Charset gbk = Charset.forName("GBK");
        try {
            return new String(bytes, gbk);
        } catch (Exception e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                deleteDirectory(child);
            }
        }
        dir.delete();
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