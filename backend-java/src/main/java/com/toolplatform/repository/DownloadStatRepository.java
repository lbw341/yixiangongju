package com.toolplatform.repository;

import com.toolplatform.model.DownloadStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DownloadStatRepository extends JpaRepository<DownloadStat, Long> {
    Optional<DownloadStat> findByToolIdAndDate(Long toolId, String date);
    List<DownloadStat> findByToolIdOrderByDate(Long toolId);

    @Query("SELECT d.date, SUM(d.count) FROM DownloadStat d GROUP BY d.date ORDER BY d.date")
    List<Object[]> getDailyTotals();

    @Query("SELECT COALESCE(SUM(d.count),0) FROM DownloadStat d WHERE d.date = :date")
    Long getTotalByDate(@Param("date") String date);
}
