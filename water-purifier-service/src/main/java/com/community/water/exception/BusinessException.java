package com.community.water.exception;

/** 业务异常：携带 HTTP 状态码语义 */
public class BusinessException extends RuntimeException {

    private final int status;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(404, what + "不存在");
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
