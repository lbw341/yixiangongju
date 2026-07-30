package com.toolplatform.controller;

import com.toolplatform.entity.User;
import com.toolplatform.repository.DownloadStatRepository;
import com.toolplatform.repository.ToolRepository;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.repository.UserToolUsageRepository;
import com.toolplatform.service.StatsService;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController extends BaseController {

    private final ToolRepository toolRepo;
    private final DownloadStatRepository statRepo;
    private final UserToolUsageRepository usageRepo;
    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final StatsService statsService;

    public StatsController(ToolRepository toolRepo, DownloadStatRepository statRepo,
                           UserToolUsageRepository usageRepo, UserRepository userRepo, JwtUtil jwtUtil,
                           StatsService statsService) {
        this.toolRepo = toolRepo;
        this.statRepo = statRepo;
        this.usageRepo = usageRepo;
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
        this.statsService = statsService;
    }

    @Override
    protected UserRepository getUserRepo() { return userRepo; }

    @Override
    protected JwtUtil getJwtUtil() { return jwtUtil; }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        return ResponseEntity.ok(statsService.getDashboard());
    }

    @GetMapping("/tool/{id}")
    public ResponseEntity<?> toolStats(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(statsService.getToolStats(id));
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }
    }

    @GetMapping("/recent_usage")
    public ResponseEntity<?> recentUsage(HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        List<Map<String, Object>> tools = statsService.getRecentUsage(u.getId());
        return ResponseEntity.ok(Map.of("tools", tools));
    }
}
