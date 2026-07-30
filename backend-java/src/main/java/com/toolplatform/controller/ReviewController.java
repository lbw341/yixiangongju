package com.toolplatform.controller;

import com.toolplatform.model.Review;
import com.toolplatform.model.User;
import com.toolplatform.repository.ReviewRepository;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewRepository reviewRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    public ReviewController(ReviewRepository reviewRepo, UserRepository userRepo, JwtUtil jwtUtil) {
        this.reviewRepo = reviewRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("")
    public ResponseEntity<?> createReview(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        Object toolIdObj = body.get("tool_id");
        String content = (String) body.getOrDefault("content", "");
        if (toolIdObj == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "工具ID和评价内容不能为空"));
        }

        Review r = new Review();
        r.setToolId(Long.valueOf(toolIdObj.toString()));
        r.setUserId(u.getId());
        r.setUsername(u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername());
        r.setContent(content.trim());
        reviewRepo.save(r);
        return ResponseEntity.status(201).body(Map.of("message", "评价提交成功"));
    }

    private User getCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        return userRepo.findById(jwtUtil.getUserIdFromToken(token)).orElse(null);
    }
}
