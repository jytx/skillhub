package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

/**
 * 管理端用户列表项。systemAccount 标记内置系统账号，前端据此隐藏删除等操作入口。
 */
public record AdminUserSummaryResponse(
        String id,
        String username,
        String email,
        String status,
        List<String> platformRoles,
        Instant createdAt,
        boolean systemAccount
) {
}
