package com.toolplatform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "download_stats", uniqueConstraints = @UniqueConstraint(columnNames = {"toolId", "date"}))
public class DownloadStat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long toolId;
    @Column(nullable = false)
    private String date;
    private int count = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
