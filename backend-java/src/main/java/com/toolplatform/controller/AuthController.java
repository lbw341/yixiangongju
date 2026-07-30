package com.toolplatform.controller;

import com.toolplatform.dto.LoginRequest;
import com.toolplatform.dto.RegisterRequest;
import com.toolplatform.model.User;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        var user = userRepo.findByUsername(req.getUsername().trim());
        if (user.isEmpty() || !encoder.matches(req.getPassword().trim(), user.get().getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        User u = user.get();
        String token = jwtUtil.generateToken(u.getId());
        return ResponseEntity.ok(Map.of("token", token, "user", toUserMap(u)));
    }

    @GetMapping("/check_username")
    public ResponseEntity<?> checkUsername(@RequestParam String username) {
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        boolean exists = userRepo.existsByUsername(username.trim());
        return ResponseEntity.ok(Map.of("exists", exists, "available", !exists));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty() ||
            req.getPassword() == null || req.getPassword().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        if (req.getPassword().trim().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码长度不能少于6位"));
        }
        if (userRepo.existsByUsername(req.getUsername().trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
        User u = new User();
        u.setUsername(req.getUsername().trim());
        u.setPasswordHash(encoder.encode(req.getPassword().trim()));
        u.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername().trim());
        String role = req.getRole();
        if (role == null || role.isEmpty() || (!role.equals("user") && !role.equals("author"))) {
            role = "user";
        }
        u.setRole(role);
        userRepo.save(u);
        String token = jwtUtil.generateToken(u.getId());
        return ResponseEntity.status(201).body(Map.of("token", token, "user", toUserMap(u)));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        return ResponseEntity.ok(toUserMap(u));
    }

    @PutMapping("/update_profile")
    public ResponseEntity<?> updateProfile(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        if (body.containsKey("nickname")) u.setNickname(body.get("nickname"));
        if (body.containsKey("company")) u.setCompany(body.get("company"));
        if (body.containsKey("department")) u.setDepartment(body.get("department"));
        if (body.containsKey("bio")) u.setBio(body.get("bio"));
        if (body.containsKey("email")) u.setEmail(body.get("email"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").isEmpty()) {
            if (body.get("password").length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "密码长度不能少于6位"));
            }
            u.setPasswordHash(encoder.encode(body.get("password")));
        }
        userRepo.save(u);
        return ResponseEntity.ok(toUserMap(u));
    }

    private User getCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        Long userId = jwtUtil.getUserIdFromToken(token);
        return userRepo.findById(userId).orElse(null);
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(HttpServletRequest request, @PathVariable Long userId, @RequestBody Map<String, String> body) {
        User currentUser = getCurrentUser(request);
        if (currentUser == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        if (!"admin".equals(currentUser.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "只有管理员可以修改用户角色"));
        }

        var opt = userRepo.findById(userId);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));

        User targetUser = opt.get();
        String newRole = body.get("role");
        if (newRole == null || newRole.isEmpty() || (!newRole.equals("user") && !newRole.equals("author") && !newRole.equals("admin"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的角色类型"));
        }

        targetUser.setRole(newRole);
        userRepo.save(targetUser);
        return ResponseEntity.ok(Map.of("message", "角色修改成功", "user", toUserMap(targetUser)));
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(HttpServletRequest request) {
        User currentUser = getCurrentUser(request);
        if (currentUser == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        if (!"admin".equals(currentUser.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "只有管理员可以查看用户列表"));
        }

        return ResponseEntity.ok(userRepo.findAll().stream().map(this::toUserMap).toList());
    }

    private Map<String, Object> toUserMap(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("role", u.getRole());
        m.put("company", u.getCompany());
        m.put("department", u.getDepartment());
        m.put("bio", u.getBio());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("avatar", u.getAvatar());
        return m;
    }
}
