package com.toolplatform.controller;

import com.toolplatform.dto.LoginRequest;
import com.toolplatform.dto.RegisterRequest;
import com.toolplatform.entity.User;
import com.toolplatform.service.AuthService;
import com.toolplatform.util.JwtUtil;
import com.toolplatform.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {

    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    public AuthController(UserRepository userRepo, JwtUtil jwtUtil, AuthService authService) {
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    @Override
    protected UserRepository getUserRepo() { return userRepo; }

    @Override
    protected JwtUtil getJwtUtil() { return jwtUtil; }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            Map<String, Object> result = authService.login(req);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/check_username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        if (username == null || username.trim().isEmpty()) {
            return badRequest("用户名不能为空");
        }
        boolean exists = authService.existsByUsername(username);
        return ResponseEntity.ok(Map.of("exists", exists, "available", !exists));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            Map<String, Object> result = authService.register(req);
            return ResponseEntity.status(201).body(result);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        return ResponseEntity.ok(authService.toUserMap(u));
    }

    @PutMapping("/update_profile")
    public ResponseEntity<?> updateProfile(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        try {
            Map<String, Object> result = authService.updateProfile(u.getId(), body);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(HttpServletRequest request, @PathVariable Long userId, @RequestBody Map<String, String> body) {
        User currentUser = getCurrentUser(request);
        if (currentUser == null) return unauthorized();
        try {
            Map<String, Object> result = authService.updateUserRole(userId, body.get("role"), currentUser.getRole());
            return ResponseEntity.ok(Map.of("message", "角色修改成功", "user", result));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("管理员")) {
                return forbidden(e.getMessage());
            }
            if (e.getMessage().contains("不存在")) {
                return notFound(e.getMessage());
            }
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(HttpServletRequest request) {
        User currentUser = getCurrentUser(request);
        if (currentUser == null) return unauthorized();
        try {
            return ResponseEntity.ok(authService.listUsers(currentUser.getRole()));
        } catch (IllegalArgumentException e) {
            return forbidden(e.getMessage());
        }
    }
}
