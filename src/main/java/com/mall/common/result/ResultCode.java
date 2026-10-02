package com.mall.common.result;

/**
 * 业务错误码
 */
public enum ResultCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),

    LOGIN_FAILED(1001, "用户名或密码错误"),
    USER_NOT_FOUND(1002, "用户不存在"),
    USER_DISABLED(1003, "账号已被禁用"),
    PRODUCT_NOT_FOUND(2001, "商品不存在"),
    PRODUCT_STATUS_ILLEGAL(2002, "商品状态不合法"),
    IMAGE_UPLOAD_FAILED(2003, "图片上传失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
