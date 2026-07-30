package com.toolplatform.dto;

import java.util.Map;

public class ApiResponse {
    private String message;
    private String error;
    private Object data;

    public ApiResponse() {}

    public static ApiResponse ok(String message) {
        ApiResponse r = new ApiResponse();
        r.message = message;
        return r;
    }

    public static ApiResponse error(String error) {
        ApiResponse r = new ApiResponse();
        r.error = error;
        return r;
    }

    public static ApiResponse data(Object data) {
        ApiResponse r = new ApiResponse();
        r.data = data;
        return r;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
