package com.community.water.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 轻量鉴权：从请求头读取角色与身份，按路径前缀校验角色。
 * 仅用于演示"测试账号"，不是完整安全方案。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ATTR = "currentUser";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String roleHeader = request.getHeader(CurrentUsers.HEADER_ROLE);
        String userId = request.getHeader(CurrentUsers.HEADER_USER);
        if (roleHeader == null || userId == null) {
            throw new ApiException(401, "未认证，缺少请求头 X-Role / X-User-Id");
        }
        final UserRole role;
        try {
            role = UserRole.valueOf(roleHeader.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(401, "未知角色: " + roleHeader);
        }
        Long communityId = parseLong(request.getHeader(CurrentUsers.HEADER_COMMUNITY));

        UserRole required = requiredRole(request.getRequestURI());
        if (required != null && required != role) {
            throw new ApiException(403, "路径需要 " + required + " 角色，当前为 " + role);
        }
        request.setAttribute(USER_ATTR, new CurrentUser(userId, role, communityId));
        return true;
    }

    private UserRole requiredRole(String uri) {
        if (uri.startsWith("/api/residents/")) {
            return UserRole.RESIDENT;
        }
        if (uri.startsWith("/api/technician/")) {
            return UserRole.TECHNICIAN;
        }
        if (uri.startsWith("/api/property/")) {
            return UserRole.PROPERTY;
        }
        if (uri.startsWith("/api/ops/")) {
            return UserRole.OPERATOR;
        }
        return null;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
