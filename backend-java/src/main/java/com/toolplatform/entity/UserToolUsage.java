package com.toolplatform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_tool_usage", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "toolId"}))
public class UserToolUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long toolId;
    private String lastUsed;
    private int useCount = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }
    public String getLastUsed() { return lastUsed; }
    public void setLastUsed(String lastUsed) { this.lastUsed = lastUsed; }
    public int getUseCount() { return useCount; }
    public void setUseCount(int useCount) { this.useCount = useCount; }
}
