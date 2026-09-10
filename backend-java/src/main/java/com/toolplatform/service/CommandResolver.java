package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/**
 * 解释器/命令探测工具：带双重检查锁的懒加载。
 * resolveXxx() 返回 null 表示服务器没装这个命令（脚本会收到 runtimeMissing）。
 */
@Component
public class CommandResolver {
    private volatile boolean nodeChecked;
    private volatile String cachedNode;
    private volatile boolean javaChecked;
    private volatile boolean javaPresent;
    private volatile boolean pythonChecked;
    private volatile boolean pythonPresent;
    private volatile boolean bashChecked;
    private volatile String cachedBash;
    private volatile boolean cmdChecked;
    private volatile String cachedCmd;
    private final Object lock = new Object();

    public String resolveNode() {
        if (nodeChecked) return cachedNode;
        synchronized (lock) {
            if (!nodeChecked) { cachedNode = detect("node"); nodeChecked = true; }
        }
        return cachedNode;
    }

    public boolean isJavaPresent() {
        if (!javaChecked) {
            synchronized (lock) {
                if (!javaChecked) { javaPresent = detect("java") != null; javaChecked = true; }
            }
        }
        return javaPresent;
    }

    public boolean isPythonPresent() {
        if (!pythonChecked) {
            synchronized (lock) {
                if (!pythonChecked) { pythonPresent = detect("python") != null; pythonChecked = true; }
            }
        }
        return pythonPresent;
    }

    /** 探测 bash：Windows 上优先找 Git Bash / WSL bash，不行就 bash 本身 */
    public String resolveBash() {
        if (bashChecked) return cachedBash;
        synchronized (lock) {
            if (!bashChecked) {
                cachedBash = firstExisting(
                        // Windows 常见路径（不依赖 PATH）
                        "C:\\Program Files\\Git\\bin\\bash.exe",
                        "C:\\Program Files (x86)\\Git\\bin\\bash.exe",
                        "C:\\Program Files\\Git\\usr\\bin\\bash.exe",
                        // PATH 里找
                        detect("bash")
                );
                bashChecked = true;
            }
        }
        return cachedBash;
    }

    /** 探测 cmd（Windows 自带，基本不会失败） */
    public String resolveCmd() {
        if (cmdChecked) return cachedCmd;
        synchronized (lock) {
            if (!cmdChecked) {
                cachedCmd = firstExisting(
                        System.getenv("ComSpec"),   // %ComSpec% → C:\Windows\System32\cmd.exe
                        detect("cmd")
                );
                cmdChecked = true;
            }
        }
        return cachedCmd;
    }

    private String detect(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
            Process p = pb.start();
            boolean ok = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            p.destroyForcibly();
            return ok ? cmd : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String firstExisting(String... paths) {
        if (paths == null) return null;
        for (String p : paths) {
            if (p != null && !p.isEmpty() && new java.io.File(p).exists()) return p;
        }
        return null;
    }
}
