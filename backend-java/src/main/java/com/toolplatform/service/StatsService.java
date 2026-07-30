package com.toolplatform.service;

import com.toolplatform.entity.DownloadStat;
import com.toolplatform.entity.Tool;
import com.toolplatform.entity.UserToolUsage;
import com.toolplatform.repository.DownloadStatRepository;
import com.toolplatform.repository.ToolRepository;
import com.toolplatform.repository.UserToolUsageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计服务层
 * 处理仪表盘、工具统计、使用记录等业务逻辑
 */
@Service
public class StatsService {

    private final ToolRepository toolRepo;
    private final DownloadStatRepository statRepo;
    private final UserToolUsageRepository usageRepo;

    public StatsService(ToolRepository toolRepo, DownloadStatRepository statRepo, UserToolUsageRepository usageRepo) {
        this.toolRepo = toolRepo;
        this.statRepo = statRepo;
        this.usageRepo = usageRepo;
    }

    /**
     * 获取仪表盘数据
     */
    public Map<String, Object> getDashboard() {
        String[] categories = {"规划", "建设", "维护", "优化", "客服"};
        Map<String, Object> catStats = new LinkedHashMap<>();
        for (String cat : categories) {
            var tools = toolRepo.findByCategoryAndStatus(cat, "online");
            int downloads = tools.stream().mapToInt(Tool::getDownloads).sum();
            int calls = tools.stream().mapToInt(Tool::getCalls).sum();
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
                        Tool::getAuthorName,
                        Collectors.summingInt(t -> 1)
                ))
                .entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> Map.of("author_name", e.getKey(), "tool_count", e.getValue()))
                .collect(Collectors.toList());

        String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Long todayDownloads = statRepo.getTotalByDate(today);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", catStats);
        result.put("hot_tools", hotTools);
        result.put("author_stats", authorStats);
        result.put("today_downloads", todayDownloads);
        return result;
    }

    /**
     * 获取单个工具的统计数据
     */
    public Map<String, Object> getToolStats(Long id) {
        Tool tool = toolRepo.findById(id).orElse(null);
        if (tool == null) throw new IllegalArgumentException("工具不存在");

        var trend = statRepo.findByToolIdOrderByDate(id).stream()
                .map(s -> Map.of("date", s.getDate(), "count", s.getCount()))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool_id", id);
        result.put("downloads", tool.getDownloads());
        result.put("calls", tool.getCalls());
        result.put("trend", trend);
        return result;
    }

    /**
     * 获取用户最近使用记录
     */
    public List<Map<String, Object>> getRecentUsage(Long userId) {
        return usageRepo.findByUserIdOrderByLastUsedDesc(userId).stream()
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
    }
}
