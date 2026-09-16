package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

/**
 * 管理端用户列表项。systemAccount 标记内置系统账号；deletable 由后端统一计算
 * （非系统账号且非引导管理员账号），前端据此收敛删除入口。
 */
public record AdminUserSummaryResponse(
        String id,
        String username,
        String email,
        String status,
        List<String> platformRoles,
        Instant createdAt,
        boolean systemAccount,
        boolean deletable
) {
}
