package com.toolplatform.service;

import com.toolplatform.dto.LoginRequest;
import com.toolplatform.dto.RegisterRequest;
import com.toolplatform.entity.User;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务层
 * 处理用户认证、注册、角色管理等业务逻辑
 */
@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 用户登录
     */
    public Map<String, Object> login(LoginRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        var user = userRepo.findByUsername(req.getUsername().trim());
        if (user.isEmpty() || !encoder.matches(req.getPassword().trim(), user.get().getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        User u = user.get();
        String token = jwtUtil.generateToken(u.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", toUserMap(u));
        return result;
    }

    /**
     * 用户注册
     */
    public Map<String, Object> register(RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty() ||
            req.getPassword() == null || req.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        if (req.getPassword().trim().length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        if (userRepo.existsByUsername(req.getUsername().trim())) {
            throw new IllegalArgumentException("用户名已存在");
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
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", toUserMap(u));
        return result;
    }

    /**
     * 检查用户名是否存在
     */
    public boolean existsByUsername(String username) {
        return userRepo.existsByUsername(username.trim());
    }

    /**
     * 更新用户资料
     */
    public Map<String, Object> updateProfile(Long userId, Map<String, String> body) {
        User u = userRepo.findById(userId).orElse(null);
        if (u == null) throw new IllegalArgumentException("用户不存在");

        if (body.containsKey("nickname")) u.setNickname(body.get("nickname"));
        if (body.containsKey("company")) u.setCompany(body.get("company"));
        if (body.containsKey("department")) u.setDepartment(body.get("department"));
        if (body.containsKey("bio")) u.setBio(body.get("bio"));
        if (body.containsKey("email")) u.setEmail(body.get("email"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").isEmpty()) {
            if (body.get("password").length() < 6) {
                throw new IllegalArgumentException("密码长度不能少于6位");
            }
            u.setPasswordHash(encoder.encode(body.get("password")));
        }
        userRepo.save(u);
        return toUserMap(u);
    }

    /**
     * 更新用户角色（管理员）
     */
    public Map<String, Object> updateUserRole(Long userId, String newRole, String currentUserRole) {
        if (!"admin".equals(currentUserRole)) {
            throw new IllegalArgumentException("只有管理员可以修改用户角色");
        }
        User targetUser = userRepo.findById(userId).orElse(null);
        if (targetUser == null) throw new IllegalArgumentException("用户不存在");
        if (newRole == null || newRole.isEmpty() || (!newRole.equals("user") && !newRole.equals("author") && !newRole.equals("admin"))) {
            throw new IllegalArgumentException("无效的角色类型");
        }
        targetUser.setRole(newRole);
        userRepo.save(targetUser);
        return toUserMap(targetUser);
    }

    /**
     * 获取所有用户（管理员）
     */
    public java.util.List<Map<String, Object>> listUsers(String currentUserRole) {
        if (!"admin".equals(currentUserRole)) {
            throw new IllegalArgumentException("只有管理员可以查看用户列表");
        }
        return userRepo.findAll().stream().map(this::toUserMap).toList();
    }

    /**
     * 转换用户为Map
     */
    public Map<String, Object> toUserMap(User u) {
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

    /**
     * 根据ID获取用户
     */
    public User getUserById(Long userId) {
        return userRepo.findById(userId).orElse(null);
    }
}
