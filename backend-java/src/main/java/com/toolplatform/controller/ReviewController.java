package com.toolplatform.controller;

import com.toolplatform.entity.Review;
import com.toolplatform.entity.User;
import com.toolplatform.repository.ReviewRepository;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController extends BaseController {

    private final ReviewRepository reviewRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    public ReviewController(ReviewRepository reviewRepo, UserRepository userRepo, JwtUtil jwtUtil) {
        this.reviewRepo = reviewRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected UserRepository getUserRepo() { return userRepo; }

    @Override
    protected JwtUtil getJwtUtil() { return jwtUtil; }

    @GetMapping("")
    public ResponseEntity<?> listReviews(@RequestParam(required = false) Long toolId) {
        List<Review> reviews;
        if (toolId != null) {
            reviews = reviewRepo.findByToolIdOrderByCreatedAtDesc(toolId);
        } else {
            reviews = reviewRepo.findAll();
        }
        return ResponseEntity.ok(Map.of("reviews", reviews));
    }

    @PostMapping("")
    public ResponseEntity<?> createReview(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();

        Object toolIdObj = body.get("tool_id");
        String content = (String) body.getOrDefault("content", "");
        if (toolIdObj == null || content.trim().isEmpty()) {
            return badRequest("工具ID和评价内容不能为空");
        }

        Review r = new Review();
        r.setToolId(Long.valueOf(toolIdObj.toString()));
        r.setUserId(u.getId());
        r.setUsername(u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername());
        r.setContent(content.trim());
        reviewRepo.save(r);
        return ResponseEntity.status(201).body(Map.of("message", "评价提交成功"));
    }
}
