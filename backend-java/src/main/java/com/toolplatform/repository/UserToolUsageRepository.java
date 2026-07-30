package com.toolplatform.repository;

import com.toolplatform.model.UserToolUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserToolUsageRepository extends JpaRepository<UserToolUsage, Long> {
    Optional<UserToolUsage> findByUserIdAndToolId(Long userId, Long toolId);
    List<UserToolUsage> findByUserIdOrderByLastUsedDesc(Long userId);
}
