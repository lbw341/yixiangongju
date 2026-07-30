package com.toolplatform.controller;

import com.toolplatform.model.User;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    @Value("${upload.result-dir}")
    private String resultDir;

    public FileController(UserRepository userRepo, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<?> downloadFile(@PathVariable String filename, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        Path filePath = Paths.get(resultDir, filename);
        File file = filePath.toFile();
        if (!file.exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "文件不存在"));
        }

        Resource resource = new FileSystemResource(file);
        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (Exception e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(file.length())
                .body(resource);
    }

    @GetMapping("/preview/{filename}")
    public ResponseEntity<?> previewFile(@PathVariable String filename, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        Path filePath = Paths.get(resultDir, filename);
        File file = filePath.toFile();
        if (!file.exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "文件不存在"));
        }

        try {
            String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";
            if (ext.equals("txt") || ext.equals("log") || ext.equals("md") || ext.equals("csv")) {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                return ResponseEntity.ok(Map.of("content", content, "filename", filename));
            } else {
                return ResponseEntity.status(400).body(Map.of("error", "不支持预览该文件类型，仅支持文本文件(.txt, .log, .md, .csv)"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "预览失败: " + e.getMessage()));
        }
    }

    private User getCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        return userRepo.findById(jwtUtil.getUserIdFromToken(token)).orElse(null);
    }
}
