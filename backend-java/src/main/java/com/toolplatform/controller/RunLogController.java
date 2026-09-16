package com.toolplatform.controller;

import com.toolplatform.entity.User;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.service.RunLogService;
import com.toolplatform.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/run-logs")
public class RunLogController extends BaseController {

    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final RunLogService runLogService;

    public RunLogController(UserRepository userRepo, JwtUtil jwtUtil, RunLogService runLogService) {
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
        this.runLogService = runLogService;
    }

    @Override
    protected UserRepository getUserRepo() { return userRepo; }

    @Override
    protected JwtUtil getJwtUtil() { return jwtUtil; }

    @GetMapping("")
    public ResponseEntity<?> list(@RequestParam(name = "tool", required = false) String tool,
                                  @RequestParam(name = "user", required = false) String user,
                                  @RequestParam(name = "startDate", required = false) String startDate,
                                  @RequestParam(name = "endDate", required = false) String endDate,
                                  @RequestParam(name = "status", required = false) String status,
                                  @RequestParam(name = "page", defaultValue = "1") int page,
                                  @RequestParam(name = "size", defaultValue = "20") int size,
                                  HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        if (!"admin".equals(u.getRole())) return forbidden("只有管理员可以查看运行日志");

        RunLogService.RunLogQuery query = new RunLogService.RunLogQuery(tool, user, null, null, status);
        if (startDate != null && !startDate.isBlank()) {
            try {
                query.start = LocalDate.parse(startDate).atStartOfDay();
            } catch (DateTimeParseException e) {
                return badRequest("日期格式应为 yyyy-MM-dd");
            }
        }
        if (endDate != null && !endDate.isBlank()) {
            try {
                query.endExclusive = LocalDate.parse(endDate).plusDays(1).atStartOfDay();
            } catch (DateTimeParseException e) {
                return badRequest("日期格式应为 yyyy-MM-dd");
            }
        }

        RunLogService.PageResult<RunLogService.RunLogSummary> result = runLogService.query(query, page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getItems(),
                "total", result.getTotal(),
                "page", result.getPage(),
                "size", result.getSize()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return unauthorized();
        if (!"admin".equals(u.getRole())) return forbidden("只有管理员可以查看运行日志");

        RunLogService.RunLogDetail detail = runLogService.getDetail(id);
        if (detail == null) return notFound("运行日志不存在");
        return ResponseEntity.ok(detail);
    }
}
