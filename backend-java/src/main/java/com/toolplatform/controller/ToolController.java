package com.toolplatform.controller;

import com.toolplatform.model.*;
import com.toolplatform.repository.*;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

@RestController
@RequestMapping("/api/tools")
public class ToolController {
    private final ToolRepository toolRepo;
    private final ReviewRepository reviewRepo;
    private final DownloadStatRepository statRepo;
    private final UserToolUsageRepository usageRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    @Value("${upload.template-dir}")
    private String templateDir;

    @Value("${upload.result-dir}")
    private String resultDir;
 
    private static final Set<String> ALLOWED_EXT = Set.of("xlsx","csv","json","zip","py","sh","bat","ps1","txt","xls");
    private static final Set<String> TEXT_EXT = Set.of("txt", "md", "log", "py", "sh", "bat", "ps1", "json", "csv");
    private static final Set<String> EXCEL_EXT = Set.of("xlsx", "xls");

    public ToolController(ToolRepository toolRepo, ReviewRepository reviewRepo,
                          DownloadStatRepository statRepo, UserToolUsageRepository usageRepo,
                          UserRepository userRepo, JwtUtil jwtUtil) {
        this.toolRepo = toolRepo;
        this.reviewRepo = reviewRepo;
        this.statRepo = statRepo;
        this.usageRepo = usageRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("")
    public ResponseEntity<?> listTools(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int per_page) {

        List<Tool> tools;
        if (search != null && !search.trim().isEmpty()) {
            tools = toolRepo.searchTools(search.trim());
        } else if (category != null && !category.isEmpty()) {
            tools = toolRepo.findByCategoryAndStatus(category, "online");
        } else {
            tools = toolRepo.findByStatus("online");
        }
        tools.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return ResponseEntity.ok(Map.of("tools", tools, "total", tools.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getTool(@PathVariable Long id) {
        var tool = toolRepo.findById(id);
        if (tool.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));
        Tool t = tool.get();
        List<Review> reviews = reviewRepo.findByToolIdOrderByCreatedAtDesc(id);
        Map<String, Object> result = toToolMap(t);
        result.put("reviews", reviews);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/download_template")
    public ResponseEntity<?> downloadTemplate(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var tool = toolRepo.findById(id);
        if (tool.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));
        Tool t = tool.get();
        if (t.getTemplateFile() == null || t.getTemplateFile().isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "该工具没有模板文件"));
        }

        Path filePath = Paths.get(templateDir, t.getTemplateFile());
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(404).body(Map.of("error", "模板文件不存在"));
        }

        t.setDownloads(t.getDownloads() + 1);
        toolRepo.save(t);

        String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        var stat = statRepo.findByToolIdAndDate(id, today);
        if (stat.isPresent()) {
            stat.get().setCount(stat.get().getCount() + 1);
            statRepo.save(stat.get());
        } else {
            DownloadStat ds = new DownloadStat();
            ds.setToolId(id);
            ds.setDate(today);
            ds.setCount(1);
            statRepo.save(ds);
        }

        var usage = usageRepo.findByUserIdAndToolId(u.getId(), id);
        if (usage.isPresent()) {
            usage.get().setUseCount(usage.get().getUseCount() + 1);
            usage.get().setLastUsed(LocalDateTime.now().toString());
            usageRepo.save(usage.get());
        } else {
            UserToolUsage utu = new UserToolUsage();
            utu.setUserId(u.getId());
            utu.setToolId(id);
            utu.setLastUsed(LocalDateTime.now().toString());
            utu.setUseCount(1);
            usageRepo.save(utu);
        }

        Resource resource = new FileSystemResource(filePath.toFile());
        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (Exception e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.getTemplateFile() + "\"")
                .contentLength(filePath.toFile().length())
                .body(resource);
    }

    private String readExcelContent(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        Workbook workbook = null;
        
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
            
            if (isXlsx) {
                workbook = new XSSFWorkbook(inputStream);
            } else {
                workbook = new HSSFWorkbook(inputStream);
            }
            
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> rowData = new ArrayList<>();
                for (Cell cell : row) {
                    String cellValue = "";
                    switch (cell.getCellType()) {
                        case STRING:
                            cellValue = cell.getStringCellValue();
                            break;
                        case NUMERIC:
                            if (DateUtil.isCellDateFormatted(cell)) {
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

    private String executePythonCode(String code, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
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
        
        System.out.println("Executing command: " + String.join(" ", command));
        System.out.println("Working directory: " + workDir);
        
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
    
    private String decodeOutput(byte[] bytes) {
        if (bytes.length == 0) return "";
        
        Charset gbk = Charset.forName("GBK");
        try {
            return new String(bytes, gbk);
        } catch (Exception e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    
    private int scoreDecoding(String text) {
        int score = 0;
        boolean hasChinese = false;
        for (char c : text.toCharArray()) {
            if (c == '\uFFFD') score -= 100;
            if (c >= 0x4E00 && c <= 0x9FFF) {
                score += 10;
                hasChinese = true;
            }
            if (Character.isLetterOrDigit(c)) score += 1;
            if (c == '\n' || c == '\r' || c == ' ') score += 2;
        }
        if (hasChinese) score += 50;
        return score;
    }
    
    private String decodeTextContent(byte[] bytes) {
        return decodeOutput(bytes);
    }
    
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            for (File child : dir.listFiles()) {
                deleteDirectory(child);
            }
        }
        dir.delete();
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

    @PostMapping("/create")
    public ResponseEntity<?> createTool(HttpServletRequest request, @RequestParam Map<String, String> form,
                                         @RequestParam(value = "template_file", required = false) MultipartFile file) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        String name = form.getOrDefault("name", "").trim();
        String type = form.getOrDefault("type", "").trim();
        String category = form.getOrDefault("category", "").trim();
        if (name.isEmpty() || type.isEmpty() || category.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "工具名称、类型和分类不能为空"));
        }

        Tool tool = new Tool();
        tool.setName(name);
        tool.setType(type);
        tool.setCategory(category);
        tool.setKeywords(form.getOrDefault("keywords", ""));
        tool.setDescription(form.getOrDefault("description", ""));
        tool.setDepartment(form.getOrDefault("department", ""));
        tool.setAuthorId(u.getId());
        tool.setAuthorName(u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername());
        tool.setContactEmail(form.getOrDefault("contact_email", ""));
        tool.setContactPhone(form.getOrDefault("contact_phone", ""));
        tool.setInstructions(form.getOrDefault("instructions", ""));
        tool.setStatus("online");

        if (file != null && !file.isEmpty()) {
            try {
                String fileName = file.getOriginalFilename();
                String uniqueName = System.currentTimeMillis() + "_" + fileName;
                Path uploadPath = Paths.get(templateDir, uniqueName);
                Files.copy(file.getInputStream(), uploadPath, StandardCopyOption.REPLACE_EXISTING);
                tool.setTemplateFile(uniqueName);
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "模板上传失败"));
            }
        }

        toolRepo.save(tool);
        return ResponseEntity.status(201).body(Map.of("message", "工具创建成功", "tool_id", tool.getId()));
    }

    @PutMapping("/{id}/update")
    public ResponseEntity<?> updateTool(@PathVariable Long id, HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var opt = toolRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));

        Tool tool = opt.get();
        if (!"admin".equals(u.getRole()) && !tool.getAuthorId().equals(u.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "无权修改此工具"));
        }

        String[] fields = {"name","type","category","keywords","description","department","contact_email","contact_phone","instructions","faq","status"};
        for (String f : fields) {
            if (body.containsKey(f)) {
                switch (f) {
                    case "name": tool.setName(body.get(f)); break;
                    case "type": tool.setType(body.get(f)); break;
                    case "category": tool.setCategory(body.get(f)); break;
                    case "keywords": tool.setKeywords(body.get(f)); break;
                    case "description": tool.setDescription(body.get(f)); break;
                    case "department": tool.setDepartment(body.get(f)); break;
                    case "contact_email": tool.setContactEmail(body.get(f)); break;
                    case "contact_phone": tool.setContactPhone(body.get(f)); break;
                    case "instructions": tool.setInstructions(body.get(f)); break;
                    case "faq": tool.setFaq(body.get(f)); break;
                    case "status": tool.setStatus(body.get(f)); break;
                }
            }
        }
        tool.setUpdatedAt(LocalDateTime.now());
        toolRepo.save(tool);
        return ResponseEntity.ok(Map.of("message", "工具更新成功"));
    }

    @PostMapping("/{id}/offline")
    public ResponseEntity<?> offlineTool(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var opt = toolRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));

        Tool tool = opt.get();
        if (!"admin".equals(u.getRole()) && !tool.getAuthorId().equals(u.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此工具"));
        }

        tool.setStatus("offline");
        toolRepo.save(tool);
        return ResponseEntity.ok(Map.of("message", "工具已下线"));
    }

    @PostMapping("/{id}/online")
    public ResponseEntity<?> onlineTool(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var opt = toolRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));

        Tool tool = opt.get();
        if (!"admin".equals(u.getRole()) && !tool.getAuthorId().equals(u.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此工具"));
        }

        tool.setStatus("online");
        toolRepo.save(tool);
        return ResponseEntity.ok(Map.of("message", "工具已上线"));
    }

    @GetMapping("/my")
    public ResponseEntity<?> myTools(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        List<Tool> tools = toolRepo.findByAuthorId(u.getId());
        tools.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return ResponseEntity.ok(Map.of("tools", tools));
    }

    private User getCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        return userRepo.findById(jwtUtil.getUserIdFromToken(token)).orElse(null);
    }

    private Map<String, Object> toToolMap(Tool t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("type", t.getType());
        m.put("category", t.getCategory());
        m.put("keywords", t.getKeywords());
        m.put("description", t.getDescription());
        m.put("department", t.getDepartment());
        m.put("authorId", t.getAuthorId());
        m.put("authorName", t.getAuthorName());
        m.put("templateFile", t.getTemplateFile());
        m.put("status", t.getStatus());
        m.put("downloads", t.getDownloads());
        m.put("calls", t.getCalls());
        m.put("contactEmail", t.getContactEmail());
        m.put("contactPhone", t.getContactPhone());
        m.put("instructions", t.getInstructions());
        m.put("faq", t.getFaq());
        m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "");
        m.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : "");
        return m;
    }

    private static class ZipExtractResult {
        String content;
        List<String> processedFiles;
        Map<String, String> pythonFiles;
        Map<String, byte[]> dataFiles;
        
        ZipExtractResult() {
            this.content = "";
            this.processedFiles = new ArrayList<>();
            this.pythonFiles = new LinkedHashMap<>();
            this.dataFiles = new LinkedHashMap<>();
        }
    }

    private ZipExtractResult extractZipContent(InputStream inputStream) throws IOException {
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
                
                result.content = tempContent.toString();
                result.processedFiles = tempFiles;
                result.pythonFiles = tempPyFiles;
                result.dataFiles = tempDataFiles;
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

    @PostMapping("/{id}/upload")
    public ResponseEntity<?> uploadFile(@PathVariable Long id, 
                                        @RequestParam("file") MultipartFile[] files, 
                                        HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var tool = toolRepo.findById(id);
        if (tool.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));

        if (files == null || files.length == 0) return ResponseEntity.badRequest().body(Map.of("error", "未选择文件"));

        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String resultName = "result_" + id + "_" + u.getId() + "_" + timestamp + ".txt";
            Path resultPath = Paths.get(resultDir, resultName);

            Tool t = tool.get();
            t.setCalls(t.getCalls() + 1);
            toolRepo.save(t);

            String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            var stat = statRepo.findByToolIdAndDate(id, today);
            if (stat.isPresent()) {
                stat.get().setCount(stat.get().getCount() + 1);
                statRepo.save(stat.get());
            } else {
                DownloadStat ds = new DownloadStat();
                ds.setToolId(id);
                ds.setDate(today);
                ds.setCount(1);
                statRepo.save(ds);
            }

            StringBuilder allContent = new StringBuilder();
            List<String> processedFiles = new ArrayList<>();
            Map<String, String> allPythonFiles = new LinkedHashMap<>();
            Map<String, byte[]> allDataFiles = new LinkedHashMap<>();
            StringBuilder pyOutput = new StringBuilder();

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
                    allContent.append(zipResult.content);
                    processedFiles.addAll(zipResult.processedFiles);
                    allPythonFiles.putAll(zipResult.pythonFiles);
                    allDataFiles.putAll(zipResult.dataFiles);
                } else if ("txt".equalsIgnoreCase(ext) || "md".equalsIgnoreCase(ext) || "log".equalsIgnoreCase(ext) || "json".equalsIgnoreCase(ext) || "csv".equalsIgnoreCase(ext)) {
                    byte[] fileBytes = file.getInputStream().readAllBytes();
                    String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
                    allContent.append("===== 文件: ").append(originalName).append(" =====\n");
                    allContent.append(fileContent);
                    allContent.append("\n\n");
                    processedFiles.add(originalName);
                    allDataFiles.put(originalName, fileBytes);
                } else if ("xlsx".equalsIgnoreCase(ext) || "xls".equalsIgnoreCase(ext)) {
                    byte[] fileBytes = file.getInputStream().readAllBytes();
                    String excelContent = readExcelContent(new ByteArrayInputStream(fileBytes));
                    allContent.append("===== Excel文件: ").append(originalName).append(" =====\n");
                    allContent.append(excelContent);
                    allContent.append("\n\n");
                    processedFiles.add(originalName);
                    allDataFiles.put(originalName, fileBytes);
                } else if ("py".equalsIgnoreCase(ext)) {
                    String code = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    allPythonFiles.put(originalName, code);
                    processedFiles.add(originalName);
                }
            }

            if (!allPythonFiles.isEmpty()) {
                for (Map.Entry<String, String> entry : allPythonFiles.entrySet()) {
                    pyOutput.append("===== Python文件: ").append(entry.getKey()).append(" =====\n");
                    try {
                        String output = executePythonCode(entry.getValue(), allDataFiles);
                        pyOutput.append(output);
                    } catch (Exception e) {
                        pyOutput.append("执行错误: ").append(e.getMessage());
                    }
                    pyOutput.append("\n\n");
                }
                Files.writeString(resultPath, pyOutput.toString(), StandardCharsets.UTF_8);
                return ResponseEntity.ok(Map.of(
                        "message", "Python代码执行完成，共执行 " + allPythonFiles.size() + " 个文件", 
                        "result_file", resultName, 
                        "processed_files", processedFiles, 
                        "output", pyOutput.toString()
                ));
            }

            if (allContent.length() > 0) {
                String weeklyReport = generateWeeklyReport(allContent.toString());
                Files.writeString(resultPath, weeklyReport, StandardCharsets.UTF_8);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "文件上传成功，共处理 " + processedFiles.size() + " 个文件", 
                    "result_file", resultName, 
                    "processed_files", processedFiles
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "文件处理失败: " + e.getMessage()));
        }
    }

    private String generateWeeklyReport(String content) {
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
}
