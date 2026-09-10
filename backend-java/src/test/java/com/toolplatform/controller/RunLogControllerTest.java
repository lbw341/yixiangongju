package com.toolplatform.controller;

import com.toolplatform.entity.User;
import com.toolplatform.repository.UserRepository;
import com.toolplatform.service.RunLogService;
import com.toolplatform.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RunLogControllerTest {

    private JwtUtil stubJwt() {
        return new JwtUtil("ToolPlatformSecretKey2024ForJWTTokenGenerationAndValidation", 604800000L) {
            @Override public boolean validateToken(String token) { return true; }
            @Override public Long getUserIdFromToken(String token) { return 1L; }
        };
    }

    private UserRepository stubUserRepo(User user) {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.ofNullable(user));
        return repo;
    }

    private MockHttpServletRequest authedRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer test-token");
        return req;
    }

    @Test
    void list_forbidsNonAdmin() {
        User u = new User();
        u.setId(1L);
        u.setRole("user");
        RunLogController c = new RunLogController(stubUserRepo(u), stubJwt(), mock(RunLogService.class));
        assertEquals(403, c.list(null, null, null, null, null, 1, 20, authedRequest()).getStatusCode().value());
    }

    @Test
    void list_allowsAdmin() {
        User u = new User();
        u.setId(1L);
        u.setRole("admin");
        RunLogService svc = mock(RunLogService.class);
        when(svc.query(any(), eq(1), eq(20))).thenReturn(
                new RunLogService.PageResult<>(List.of(), 0, 1, 20));
        RunLogController c = new RunLogController(stubUserRepo(u), stubJwt(), svc);
        assertEquals(200, c.list(null, null, null, null, null, 1, 20, authedRequest()).getStatusCode().value());
    }

    @Test
    void list_rejectsInvalidDate() {
        User u = new User();
        u.setId(1L);
        u.setRole("admin");
        RunLogController c = new RunLogController(stubUserRepo(u), stubJwt(), mock(RunLogService.class));
        assertEquals(400, c.list(null, null, "2026/09/10", null, null, 1, 20,
                authedRequest()).getStatusCode().value());
    }

    @Test
    void detail_returns404WhenMissing() {
        User u = new User();
        u.setId(1L);
        u.setRole("admin");
        RunLogService svc = mock(RunLogService.class);
        when(svc.getDetail(99L)).thenReturn(null);
        RunLogController c = new RunLogController(stubUserRepo(u), stubJwt(), svc);
        assertEquals(404, c.detail(99L, authedRequest()).getStatusCode().value());
    }

    @Test
    void detail_forbidsNonAdmin() {
        User u = new User();
        u.setId(1L);
        u.setRole("author");
        RunLogController c = new RunLogController(stubUserRepo(u), stubJwt(), mock(RunLogService.class));
        assertEquals(403, c.detail(1L, authedRequest()).getStatusCode().value());
    }
}
