package com.toolplatform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "run_logs", indexes = {
    @Index(name = "idx_runlog_created", columnList = "created_at"),
    @Index(name = "idx_runlog_tool", columnList = "tool_id"),
    @Index(name = "idx_runlog_user", columnList = "user_id"),
    @Index(name = "idx_runlog_status", columnList = "status")
})
public class RunLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Long id;
    @Column(name = "tool_id", nullable = false)
    private Long toolId;
    @Column(name = "tool_name")
    private String toolName;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "username")
    private String username;
    @Column(name = "nickname")
    private String nickname;
    @Column(name = "runtime")
    private String runtime;
    @Column(name = "sandbox_used")
    private boolean sandboxUsed;
    @Column(name = "exit_code")
    private int exitCode;
    @Column(name = "timed_out")
    private boolean timedOut;
    @Column(name = "status")
    private String status;
    @Column(name = "input_file_names", length = 500)
    private String inputFileNames;
    @Column(name = "output", columnDefinition = "TEXT")
    private String output;
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }
    public boolean isSandboxUsed() { return sandboxUsed; }
    public void setSandboxUsed(boolean sandboxUsed) { this.sandboxUsed = sandboxUsed; }
    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }
    public boolean isTimedOut() { return timedOut; }
    public void setTimedOut(boolean timedOut) { this.timedOut = timedOut; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputFileNames() { return inputFileNames; }
    public void setInputFileNames(String inputFileNames) { this.inputFileNames = inputFileNames; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
