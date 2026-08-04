package com.toolplatform.controller;

import com.toolplatform.entity.Message;
import com.toolplatform.entity.User;
import com.toolplatform.repository.MessageRepository;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.service.MessageService;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController extends BaseController {

    private final MessageRepository msgRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final MessageService messageService;

    public MessageController(MessageRepository msgRepo, UserRepository userRepo, JwtUtil jwtUtil, MessageService messageService) {
        this.msgRepo = msgRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
        this.messageService = messageService;
    }

    @Override
    protected UserRepository getUserRepo() { return userRepo; }

    @Override
    protected JwtUtil getJwtUtil() { return jwtUtil; }

    @GetMapping("")
    public ResponseEntity<?> listMessages(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        List<Message> msgs = messageService.listMessages(u);
        return ResponseEntity.ok(Map.of("messages", msgs));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createMessage(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        try {
            messageService.createMessage(u, body);
            return ResponseEntity.status(201).body(Map.of("message", "消息发送成功"));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<?> replyMessage(@PathVariable Long id, HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        try {
            messageService.replyMessage(id, body.getOrDefault("reply", ""));
            return ResponseEntity.ok(Map.of("message", "回复成功"));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("不存在")) {
                return notFound(e.getMessage());
            }
            return badRequest(e.getMessage());
        }
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> readMessage(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        try {
            messageService.markAsRead(id, u);
            return ResponseEntity.ok(Map.of("message", "ok"));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PutMapping("/read_all")
    public ResponseEntity<?> readAllMessages(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        messageService.markAllAsRead(u);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }
}
