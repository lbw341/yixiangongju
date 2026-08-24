package com.toolplatform.controller;

import com.toolplatform.entity.*;
import com.toolplatform.repository.*;
import com.toolplatform.service.ScriptPackageService;
import com.toolplatform.service.ToolService;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 工具控制器
 * 处理工具相关的HTTP请求
 */
@RestController
@RequestMapping("/api/tools")
public class ToolController extends BaseController {
    private final ToolRepository toolRepo;
    private final ReviewRepository reviewRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final ToolService toolService;
    private final ScriptPackageService scriptPackageService;

    public ToolController(ToolRepository toolRepo, ReviewRepository reviewRepo,
                          UserRepository userRepo, JwtUtil jwtUtil, ToolService toolService,
                          ScriptPackageService scriptPackageService) {
        this.toolRepo = toolRepo;
        this.reviewRepo = reviewRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
        this.toolService = toolService;
        this.scriptPackageService = scriptPackageService;
    }

    @Override
    protected UserRepository getUserRepo() { return userRepo; }

    @Override
    protected JwtUtil getJwtUtil() { return jwtUtil; }

    @GetMapping("")
    public ResponseEntity<?> listTools(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int per_page) {

        List<Tool> tools;
        if (search != null && !search.trim().isEmpty()) {
            tools = toolRepo.searchTools(search.trim());
        } else if (category != null && !category.isEmpty() && !"全部".equals(category)) {
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

        if (t.getPackageDir() != null && !t.getPackageDir().isEmpty()) {
            Path orig = scriptPackageService.resolveOriginalZip(id);
            Resource resource;
            long length;
            String zipName;
            if (Files.exists(orig)) {
                resource = new FileSystemResource(orig.toFile());
                length = orig.toFile().length();
                zipName = (t.getTemplateFile() != null && !t.getTemplateFile().isEmpty())
                        ? t.getTemplateFile() : t.getName();
            } else {
                if (!Files.isDirectory(scriptPackageService.resolvePayload(id))) {
                    return ResponseEntity.status(500).body(Map.of("error", "模板文件不存在"));
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try {
                    scriptPackageService.zipPayloadTo(id, bos);
                } catch (IOException e) {
                    return ResponseEntity.status(500).body(Map.of("error", "模板文件不存在"));
                }
                byte[] bytes = bos.toByteArray();
                resource = new ByteArrayResource(bytes);
                length = bytes.length;
                String base = (t.getTemplateFile() != null && !t.getTemplateFile().isEmpty())
                        ? t.getTemplateFile() : t.getName();
                int dot = base.lastIndexOf('.');
                zipName = (dot >= 0 ? base.substring(0, dot) : base) + ".zip";
            }

            toolService.updateDownloadStats(id, u.getId());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                    .contentLength(length)
                    .body(resource);
        }

        if (t.getTemplateFile() == null || t.getTemplateFile().isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "该工具没有模板文件"));
        }

        Path filePath = toolService.getTemplatePath(t.getTemplateFile());
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(404).body(Map.of("error", "模板文件不存在"));
        }

        toolService.updateDownloadStats(id, u.getId());

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

    @GetMapping("/{id}/download_format_template")
    public ResponseEntity<?> downloadFormatTemplate(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var tool = toolRepo.findById(id);
        if (tool.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));
        Tool t = tool.get();
        if (t.getFormatTemplate() == null || t.getFormatTemplate().isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "该工具没有格式模板"));
        }

        Path filePath = toolService.getTemplatePath(t.getFormatTemplate());
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(404).body(Map.of("error", "格式模板文件不存在"));
        }

        toolService.updateDownloadStats(id, u.getId());

        Resource resource = new FileSystemResource(filePath.toFile());
        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (Exception e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.getFormatTemplate() + "\"")
                .contentLength(filePath.toFile().length())
                .body(resource);
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
                String uniqueName = toolService.saveTemplateFile(file);
                tool.setTemplateFile(uniqueName);
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "模板上传失败"));
            }
        }

        toolRepo.save(tool);
        return ResponseEntity.status(201).body(Map.of("message", "工具创建成功", "tool_id", tool.getId()));
    }

    @PutMapping("/{id}/update")
    public ResponseEntity<?> updateTool(@PathVariable Long id, HttpServletRequest request,
                                        @RequestParam Map<String, String> form,
                                        @RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "format_file", required = false) MultipartFile formatFile,
                                        @RequestParam(value = "files", required = false) MultipartFile[] files) {
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
            if (form.containsKey(f)) {
                switch (f) {
                    case "name": tool.setName(form.get(f)); break;
                    case "type": tool.setType(form.get(f)); break;
                    case "category": tool.setCategory(form.get(f)); break;
                    case "keywords": tool.setKeywords(form.get(f)); break;
                    case "description": tool.setDescription(form.get(f)); break;
                    case "department": tool.setDepartment(form.get(f)); break;
                    case "contact_email": tool.setContactEmail(form.get(f)); break;
                    case "contact_phone": tool.setContactPhone(form.get(f)); break;
                    case "instructions": tool.setInstructions(form.get(f)); break;
                    case "faq": tool.setFaq(form.get(f)); break;
                    case "status": tool.setStatus(form.get(f)); break;
                }
            }
        }

        boolean clearTemplate = "1".equals(form.get("clear_template"));
        boolean clearFormat = "1".equals(form.get("clear_format"));

        if (clearTemplate) {
            tool.setTemplateFile("");
            scriptPackageService.deleteArtifacts(id);
            tool.setPackageDir("");
            tool.setEntryFile("");
        } else if (files != null && files.length > 0) {
            try {
                ScriptPackageService.PackageInstallResult pkg =
                        scriptPackageService.install(tool.getId(), Arrays.asList(files));
                tool.setPackageDir(pkg.getPackageDir());
                tool.setEntryFile(pkg.getEntryFile());
                tool.setTemplateFile(pkg.getDisplayName());
            } catch (IOException | InterruptedException e) {
                return ResponseEntity.status(400).body(Map.of(
                        "error", firstLine(e.getMessage()),
                        "install_log", e.getMessage() == null ? "" : e.getMessage()));
            }
        } else if (file != null && !file.isEmpty()) {
            try {
                tool.setTemplateFile(toolService.saveTemplateFile(file));
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "模板上传失败"));
            }
        }

        if (clearFormat) {
            tool.setFormatTemplate("");
        } else if (formatFile != null && !formatFile.isEmpty()) {
            try {
                tool.setFormatTemplate(toolService.saveTemplateFile(formatFile));
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "格式模板上传失败"));
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

    @PostMapping("")
    public ResponseEntity<?> uploadTool(HttpServletRequest request,
                                        @RequestParam Map<String, String> form,
                                        @RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "format_file", required = false) MultipartFile formatFile,
                                        @RequestParam(value = "files", required = false) MultipartFile[] files) {
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

        if (files != null && files.length > 0) {
            toolRepo.save(tool);
            try {
                ScriptPackageService.PackageInstallResult pkg =
                        scriptPackageService.install(tool.getId(), Arrays.asList(files));
                tool.setPackageDir(pkg.getPackageDir());
                tool.setEntryFile(pkg.getEntryFile());
                tool.setTemplateFile(pkg.getDisplayName());
                toolRepo.save(tool);
            } catch (IOException | InterruptedException e) {
                toolRepo.delete(tool);
                return ResponseEntity.status(400).body(Map.of(
                        "error", firstLine(e.getMessage()),
                        "install_log", e.getMessage() == null ? "" : e.getMessage()));
            }
        } else if (file != null && !file.isEmpty()) {
            try {
                String uniqueName = toolService.saveTemplateFile(file);
                tool.setTemplateFile(uniqueName);
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "模板上传失败"));
            }
        }

        if (formatFile != null && !formatFile.isEmpty()) {
            try {
                String uniqueName = toolService.saveTemplateFile(formatFile);
                tool.setFormatTemplate(uniqueName);
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "格式模板上传失败"));
            }
        }

        toolRepo.save(tool);

        return ResponseEntity.status(201).body(Map.of("message", "工具创建成功", "tool_id", tool.getId()));
    }

    private String firstLine(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        return i >= 0 ? s.substring(0, i) : s;
    }

    @PutMapping("/{id}/toggle_status")
    public ResponseEntity<?> toggleStatus(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var opt = toolRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));

        Tool tool = opt.get();
        if (!"admin".equals(u.getRole()) && !tool.getAuthorId().equals(u.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此工具"));
        }

        String newStatus = "online".equals(tool.getStatus()) ? "offline" : "online";
        tool.setStatus(newStatus);
        toolRepo.save(tool);
        return ResponseEntity.ok(Map.of("message", "状态已更新为" + ("online".equals(newStatus) ? "在线" : "离线"), "status", newStatus));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTool(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var opt = toolRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));

        Tool tool = opt.get();
        if (!"admin".equals(u.getRole()) && !tool.getAuthorId().equals(u.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "无权删除此工具"));
        }

        scriptPackageService.deleteArtifacts(id);
        toolRepo.delete(tool);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
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
            ToolService.FileProcessResult result = toolService.processUploadedFiles(id, u.getId(), files);

            if (result.isPythonExecuted()) {
                return ResponseEntity.ok(Map.of(
                        "message", "Python代码执行完成",
                        "result_file", result.getResultName(),
                        "processed_files", result.getProcessedFiles(),
                        "output", result.getPythonOutput()
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "文件上传成功，共处理 " + result.getProcessedFiles().size() + " 个文件",
                    "result_file", result.getResultName(),
                    "processed_files", result.getProcessedFiles()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "文件处理失败: " + e.getMessage()));
        }
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
        m.put("formatTemplate", t.getFormatTemplate());
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
}