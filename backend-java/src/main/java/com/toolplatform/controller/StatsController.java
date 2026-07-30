package com.toolplatform.controller;

import com.toolplatform.model.User;
import com.toolplatform.model.UserToolUsage;
import com.toolplatform.repository.*;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final ToolRepository toolRepo;
    private final DownloadStatRepository statRepo;
    private final UserToolUsageRepository usageRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;

    public StatsController(ToolRepository toolRepo, DownloadStatRepository statRepo,
                           UserToolUsageRepository usageRepo, UserRepository userRepo, JwtUtil jwtUtil) {
        this.toolRepo = toolRepo;
        this.statRepo = statRepo;
        this.usageRepo = usageRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        String[] categories = {"规划", "建设", "维护", "优化", "客服"};
        Map<String, Object> catStats = new LinkedHashMap<>();
        for (String cat : categories) {
            var tools = toolRepo.findByCategoryAndStatus(cat, "online");
            int downloads = tools.stream().mapToInt(t -> t.getDownloads()).sum();
            int calls = tools.stream().mapToInt(t -> t.getCalls()).sum();
            catStats.put(cat, Map.of("count", tools.size(), "downloads", downloads, "calls", calls));
        }

        var hotTools = toolRepo.findByStatus("online").stream()
                .sorted((a, b) -> Integer.compare((b.getDownloads() + b.getCalls()), (a.getDownloads() + a.getCalls())))
                .limit(10)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("author_name", t.getAuthorName());
                    m.put("downloads", t.getDownloads());
                    m.put("type", t.getType());
                    return m;
                })
                .collect(Collectors.toList());

        var authorStats = toolRepo.findByStatus("online").stream()
                .collect(Collectors.groupingBy(
                    t -> t.getAuthorName(),
                    Collectors.summingInt(t -> 1)
                ))
                .entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> Map.of("author_name", e.getKey(), "tool_count", e.getValue()))
                .collect(Collectors.toList());

        String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Long todayDownloads = statRepo.getTotalByDate(today);

        return ResponseEntity.ok(Map.of(
            "categories", catStats,
            "hot_tools", hotTools,
            "author_stats", authorStats,
            "today_downloads", todayDownloads
        ));
    }

    @GetMapping("/tool/{id}")
    public ResponseEntity<?> toolStats(@PathVariable Long id) {
        var tool = toolRepo.findById(id);
        if (tool.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));

        var trend = statRepo.findByToolIdOrderByDate(id).stream()
                .map(s -> Map.of("date", s.getDate(), "count", s.getCount()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "tool_id", id,
            "downloads", tool.get().getDownloads(),
            "calls", tool.get().getCalls(),
            "trend", trend
        ));
    }

    @GetMapping("/recent_usage")
    public ResponseEntity<?> recentUsage(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        List<Map<String, Object>> tools = usageRepo.findByUserIdOrderByLastUsedDesc(u.getId()).stream()
                .limit(10)
                .map(utu -> {
                    var tool = toolRepo.findById(utu.getToolId());
                    if (tool.isEmpty()) return null;
                    var t = tool.get();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("type", t.getType());
                    m.put("category", t.getCategory());
                    m.put("keywords", t.getKeywords());
                    m.put("description", t.getDescription());
                    m.put("author_name", t.getAuthorName());
                    m.put("downloads", t.getDownloads());
                    m.put("last_used", utu.getLastUsed());
                    m.put("use_count", utu.getUseCount());
                    return m;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("tools", tools));
    }

    private User getCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) return null;
        return userRepo.findById(jwtUtil.getUserIdFromToken(token)).orElse(null);
    }
}
