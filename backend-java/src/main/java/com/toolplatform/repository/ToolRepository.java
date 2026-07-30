package com.toolplatform.repository;

import com.toolplatform.entity.Tool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ToolRepository extends JpaRepository<Tool, Long> {
    List<Tool> findByStatus(String status);
    List<Tool> findByCategoryAndStatus(String category, String status);
    List<Tool> findByAuthorId(Long authorId);

    @Query("SELECT t FROM Tool t WHERE t.status = 'online' AND ( LOWER(t.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.keywords) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.authorName) LIKE LOWER(CONCAT('%',:q,'%')) )")
    List<Tool> searchTools(@Param("q") String query);

    @Query("SELECT t FROM Tool t WHERE t.status = 'online' AND t.category = :category AND ( LOWER(t.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.keywords) LIKE LOWER(CONCAT('%',:q,'%')) )")
    List<Tool> searchByCategory(@Param("category") String category, @Param("q") String query);
}
