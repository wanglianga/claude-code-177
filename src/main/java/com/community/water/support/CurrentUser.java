package com.community.water.support;

/**
 * 简单的鉴权上下文：由 {@code AuthInterceptor} 从请求头解析。
 * X-Role / X-User-Id / X-Community-Id（物业、居民按小区隔离数据）。
 */
public record CurrentUser(String userId, UserRole role, Long communityId) {
}
