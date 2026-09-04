package com.toolplatform.controller;

import com.toolplatform.entity.Category;
import com.toolplatform.entity.User;
import com.toolplatform.repository.CategoryRepository;
import com.toolplatform.repository.ToolRepository;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 分类管理（公开查列表 + 管理员 CRUD） */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepo;
    private final ToolRepository toolRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    public CategoryController(CategoryRepository categoryRepo, ToolRepository toolRepo,
                              UserRepository userRepo, JwtUtil jwtUtil) {
        this.categoryRepo = categoryRepo;
        this.toolRepo = toolRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    private User getCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        return userRepo.findById(jwtUtil.getUserIdFromToken(token)).orElse(null);
    }

    /** 公开：分类列表（带 count 统计） */
    @GetMapping
    public ResponseEntity<?> list() {
        List<Category> list = categoryRepo.findAllByOrderBySortOrderAsc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category c : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("icon", c.getIcon());
            m.put("color", c.getColor());
            m.put("sortOrder", c.getSortOrder());
            m.put("toolCount", toolRepo.countByCategoryAndStatus(c.getName(), "online"));
            m.put("createdAt", c.getCreatedAt());
            result.add(m);
        }
        return ResponseEntity.ok(Map.of("categories", result));
    }

    /** 管理员：新增分类 */
    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request, @RequestBody Map<String, String> body) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        if (!"admin".equals(u.getRole())) return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "分类名不能为空"));
        if (categoryRepo.existsByName(name)) return ResponseEntity.badRequest().body(Map.of("error", "分类已存在"));
        Category c = new Category();
        c.setName(name);
        c.setIcon(body.getOrDefault("icon", "fas fa-toolbox"));
        c.setColor(body.getOrDefault("color", "indigo"));
        if (body.containsKey("sortOrder")) {
            try { c.setSortOrder(Integer.parseInt(body.get("sortOrder"))); } catch (Exception ignored) {}
        }
        categoryRepo.save(c);
        return ResponseEntity.ok(Map.of("message", "分类创建成功", "id", c.getId()));
    }

    /** 管理员：修改分类 */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        if (!"admin".equals(u.getRole())) return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        var opt = categoryRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "分类不存在"));
        Category c = opt.get();
        if (body.containsKey("name") && !body.get("name").trim().isEmpty()) c.setName(body.get("name").trim());
        if (body.containsKey("icon")) c.setIcon(body.get("icon"));
        if (body.containsKey("color")) c.setColor(body.get("color"));
        if (body.containsKey("sortOrder")) {
            try { c.setSortOrder(Integer.parseInt(body.get("sortOrder"))); } catch (Exception ignored) {}
        }
        categoryRepo.save(c);
        return ResponseEntity.ok(Map.of("message", "分类更新成功"));
    }

    /** 管理员：删除分类 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(HttpServletRequest request, @PathVariable Long id) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        if (!"admin".equals(u.getRole())) return ResponseEntity.status(403).body(Map.of("error", "仅管理员可操作"));
        var opt = categoryRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "分类不存在"));
        long count = toolRepo.countByCategoryAndStatus(opt.get().getName(), "online");
        if (count > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "该分类下还有工具，无法删除"));
        }
        categoryRepo.delete(opt.get());
        return ResponseEntity.ok(Map.of("message", "分类删除成功"));
    }
}
