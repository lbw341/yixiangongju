package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class CommandResolver {
    private volatile boolean nodeChecked;
    private volatile String cachedNode;
    private volatile boolean javaChecked;
    private volatile boolean javaPresent;
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
}
