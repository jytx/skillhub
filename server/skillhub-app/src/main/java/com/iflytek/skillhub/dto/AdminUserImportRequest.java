package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 确认执行 Excel 批量导入的请求体：行数据来自解析预览的回传。
 *
 * <p>password 为空表示该行由服务端自动生成随机初始密码。</p>
 */
public record AdminUserImportRequest(
        @NotNull
        List<Row> rows
) {
    public record Row(
            int rowNumber,
            @NotBlank(message = "{validation.auth.local.username.notBlank}")
            String username,
            String password,
            @NotBlank(message = "{validation.auth.local.email.notBlank}")
            @Email(message = "{validation.auth.local.email.invalid}")
            String email
    ) {
    }
}
