package com.community.water.support;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 从当前请求读取鉴权用户的工具。 */
public final class CurrentUsers {

    public static final String HEADER_ROLE = "X-Role";
    public static final String HEADER_USER = "X-User-Id";
    public static final String HEADER_COMMUNITY = "X-Community-Id";

    private CurrentUsers() {
    }

    public static CurrentUser require() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            Object user = sra.getRequest().getAttribute(AuthInterceptor.USER_ATTR);
            if (user instanceof CurrentUser cu) {
                return cu;
            }
        }
        throw new ApiException(401, "未认证，缺少请求头 X-Role / X-User-Id");
    }

    public static CurrentUser requireRole(UserRole role) {
        CurrentUser user = require();
        if (user.role() != role) {
            throw new ApiException(403, "需要 " + role + " 角色，当前为 " + user.role());
        }
        return user;
    }
}
