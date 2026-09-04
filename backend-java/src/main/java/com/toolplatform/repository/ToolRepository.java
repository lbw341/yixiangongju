package com.toolplatform.repository;

import com.toolplatform.entity.Tool;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ToolRepository extends JpaRepository<Tool, Long> {
    List<Tool> findByStatus(String status);
    List<Tool> findByCategoryAndStatus(String category, String status);
    long countByCategoryAndStatus(String category, String status);
    List<Tool> findByAuthorId(Long authorId);

    @Query("select t from Tool t where t.authorId = :authorId and (lower(t.name) like lower(concat('%', :kw, '%')) or lower(t.description) like lower(concat('%', :kw, '%')))")
    Page<Tool> searchMy(@Param("authorId") Long authorId, @Param("kw") String kw, Pageable pageable);

    @Query("SELECT t FROM Tool t WHERE t.status = 'online' AND ( LOWER(t.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.keywords) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.authorName) LIKE LOWER(CONCAT('%',:q,'%')) )")
    List<Tool> searchTools(@Param("q") String query);

    @Query("SELECT t FROM Tool t WHERE t.status = 'online' AND t.category = :category AND ( LOWER(t.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(t.keywords) LIKE LOWER(CONCAT('%',:q,'%')) )")
    List<Tool> searchByCategory(@Param("category") String category, @Param("q") String query);
}
