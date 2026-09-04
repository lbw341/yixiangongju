package com.toolplatform.util;

import java.nio.file.Path;

public final class PathUtils {

    private PathUtils() {
    }

    static String sanitizeFileName(String originalName) {
        int lastSlash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        String fileName = lastSlash >= 0 ? originalName.substring(lastSlash + 1) : originalName;
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
            fileName = "file_" + Math.abs(originalName.hashCode());
        }
        return fileName;
    }

    static String basename(String normalized) {
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    public static Path resolveDataPath(Path dataDir, String name) {
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || (normalized.length() >= 2 && normalized.charAt(1) == ':')) {
            return dataDir.resolve(sanitizeFileName(name));
        }
        Path current = dataDir;
        for (String part : normalized.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                return dataDir.resolve(sanitizeFileName(basename(normalized)));
            }
            current = current.resolve(sanitizeFileName(part));
        }
        if (!current.normalize().startsWith(dataDir.normalize())
                || current.normalize().equals(dataDir.normalize())) {
            return dataDir.resolve(sanitizeFileName(basename(normalized)));
        }
        return current;
    }
}
