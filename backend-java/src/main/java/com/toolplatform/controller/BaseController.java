package com.toolplatform.controller;

import com.toolplatform.entity.User;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

/**
 * 基础控制器
 * 提供公共方法：获取当前用户、错误响应等
 */
public abstract class BaseController {

    protected abstract UserRepository getUserRepo();
    protected abstract JwtUtil getJwtUtil();

    /**
     * 获取当前登录用户
     */
    protected User getCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!getJwtUtil().validateToken(token)) return null;
        return getUserRepo().findById(getJwtUtil().getUserIdFromToken(token)).orElse(null);
    }

    /**
     * 未登录响应
     */
    protected ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(java.util.Map.of("error", "未登录"));
    }

    /**
     * 禁止访问响应
     */
    protected ResponseEntity<?> forbidden(String message) {
        return ResponseEntity.status(403).body(java.util.Map.of("error", message));
    }

    /**
     * 资源不存在响应
     */
    protected ResponseEntity<?> notFound(String message) {
        return ResponseEntity.status(404).body(java.util.Map.of("error", message));
    }

    /**
     * 错误请求响应
     */
    protected ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(java.util.Map.of("error", message));
    }
}
