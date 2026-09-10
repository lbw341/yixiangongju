package com.toolplatform.repository;

import com.toolplatform.entity.RunLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface RunLogRepository extends JpaRepository<RunLog, Long> {
    @Query("SELECT rl FROM RunLog rl WHERE " +
            "(:tool IS NULL OR rl.toolName LIKE CONCAT('%', :tool, '%')) AND " +
            "(:user IS NULL OR rl.username LIKE CONCAT('%', :user, '%') OR rl.nickname LIKE CONCAT('%', :user, '%')) AND " +
            "(:start IS NULL OR rl.createdAt >= :start) AND " +
            "(:end IS NULL OR rl.createdAt < :end) AND " +
            "(:status IS NULL OR rl.status = :status)")
    Page<RunLog> search(@Param("tool") String tool, @Param("user") String user,
                        @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                        @Param("status") String status, Pageable pageable);

    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
