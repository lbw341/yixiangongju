package com.toolplatform.exception;

/**
 * 资源未找到异常
 * 当请求的资源不存在时抛出
 */
public class ResourceNotFoundException extends RuntimeException {

    private String resourceName;
    private Long resourceId;

    public ResourceNotFoundException(String resourceName, Long resourceId) {
        super(String.format("%s 未找到 (ID: %d)", resourceName, resourceId));
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public String getResourceName() {
        return resourceName;
    }

    public Long getResourceId() {
        return resourceId;
    }
}