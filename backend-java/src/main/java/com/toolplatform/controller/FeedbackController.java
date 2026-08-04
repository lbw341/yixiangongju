package com.toolplatform.controller;

import com.toolplatform.entity.Feedback;
import com.toolplatform.entity.Message;
import com.toolplatform.entity.User;
import com.toolplatform.repository.FeedbackRepository;
import com.toolplatform.repository.MessageRepository;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController extends BaseController {

    private final FeedbackRepository feedbackRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final MessageRepository msgRepo;

    public FeedbackController(FeedbackRepository feedbackRepo, UserRepository userRepo, JwtUtil jwtUtil, MessageRepository msgRepo) {
        this.feedbackRepo = feedbackRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
        this.msgRepo = msgRepo;
    }

    @Override
    protected UserRepository getUserRepo() { return userRepo; }

    @Override
    protected JwtUtil getJwtUtil() { return jwtUtil; }

    @PostMapping("")
    public ResponseEntity<?> submitFeedback(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();

        String title = (String) body.getOrDefault("title", "");
        if (title.trim().isEmpty()) {
            return badRequest("标题不能为空");
        }

        String type = (String) body.getOrDefault("type", "功能建议");
        String content = (String) body.getOrDefault("content", "");
        String displayName = u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername();

        Feedback fb = new Feedback();
        fb.setType(type);
        fb.setTitle(title);
        fb.setContent(content);
        fb.setUserId(u.getId());
        fb.setUsername(displayName);
        feedbackRepo.save(fb);

        // 同步在消息表创建一条消息，通知所有管理员
        List<User> admins = userRepo.findAll().stream()
                .filter(admin -> "admin".equals(admin.getRole()))
                .toList();
        for (User admin : admins) {
            Message msg = new Message();
            msg.setType("问题反馈");
            msg.setTitle("[" + type + "] " + title);
            msg.setContent(content);
            msg.setFromUser(displayName);
            msg.setFromUserId(u.getId());
            msg.setToUserId(admin.getId());
            msg.setStatus("未读");
            msgRepo.save(msg);
        }

        return ResponseEntity.status(201).body(Map.of("message", "反馈提交成功"));
    }

    @GetMapping("/my")
    public ResponseEntity<?> myFeedback(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        List<Feedback> feedback = feedbackRepo.findByUserIdOrderByCreatedAtDesc(u.getId());
        return ResponseEntity.ok(Map.of("feedback", feedback));
    }

    @GetMapping("")
    public ResponseEntity<?> allFeedback(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        if (!"admin".equals(u.getRole())) {
            return forbidden("只有管理员可以查看所有反馈");
        }
        List<Feedback> feedback = feedbackRepo.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(Map.of("feedback", feedback));
    }
}
