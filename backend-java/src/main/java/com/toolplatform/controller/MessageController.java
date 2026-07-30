package com.toolplatform.controller;

import com.toolplatform.model.Message;
import com.toolplatform.model.User;
import com.toolplatform.repository.MessageRepository;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageRepository msgRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    public MessageController(MessageRepository msgRepo, UserRepository userRepo, JwtUtil jwtUtil) {
        this.msgRepo = msgRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("")
    public ResponseEntity<?> listMessages(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        List<Message> msgs;
        if ("admin".equals(u.getRole())) {
            msgs = msgRepo.findAllByOrderByCreatedAtDesc();
        } else {
            msgs = msgRepo.findByToUserIdOrFromUserIdOrderByCreatedAtDesc(u.getId(), u.getId());
        }
        return ResponseEntity.ok(Map.of("messages", msgs));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createMessage(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        String title = (String) body.getOrDefault("title", "");
        if (title.trim().isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "标题不能为空"));

        Message msg = new Message();
        msg.setType((String) body.getOrDefault("type", "问题反馈"));
        msg.setTitle(title);
        msg.setContent((String) body.getOrDefault("content", ""));
        msg.setFromUser(u.getNickname() != null && !u.getNickname().isEmpty() ? u.getNickname() : u.getUsername());
        msg.setFromUserId(u.getId());
        if (body.containsKey("to_user_id") && body.get("to_user_id") != null) {
            msg.setToUserId(Long.valueOf(body.get("to_user_id").toString()));
        }
        if (body.containsKey("tool_id") && body.get("tool_id") != null) {
            msg.setToolId(Long.valueOf(body.get("tool_id").toString()));
        }
        msg.setStatus("未读");
        msgRepo.save(msg);
        return ResponseEntity.status(201).body(Map.of("message", "消息发送成功"));
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<?> replyMessage(@PathVariable Long id, HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var opt = msgRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "消息不存在"));

        String reply = body.getOrDefault("reply", "").trim();
        if (reply.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "回复内容不能为空"));

        Message msg = opt.get();
        msg.setReplyContent(reply);
        msg.setStatus("已答复");
        msgRepo.save(msg);
        return ResponseEntity.ok(Map.of("message", "回复成功"));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> readMessage(@PathVariable Long id) {
        var opt = msgRepo.findById(id);
        if (opt.isPresent()) {
            opt.get().setStatus("已读");
            msgRepo.save(opt.get());
        }
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    private User getCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        return userRepo.findById(jwtUtil.getUserIdFromToken(token)).orElse(null);
    }
}
