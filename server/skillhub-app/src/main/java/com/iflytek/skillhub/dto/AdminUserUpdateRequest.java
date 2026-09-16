package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员编辑用户资料的请求体，仅允许修改显示名与邮箱。
 *
 * <p>登录名、角色、状态与密码不在编辑范围内：登录名是账号的稳定标识，
 * 其余各项由专门的管理端点负责。</p>
 */
public record AdminUserUpdateRequest(
    @NotBlank(message = "{validation.auth.local.username.notBlank}")
    @Size(min = 3, max = 64, message = "{validation.auth.local.username.size}")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,64}$", message = "{validation.auth.local.username.pattern}")
    String displayName,
    @NotBlank(message = "{validation.auth.local.email.notBlank}")
    @Email(message = "{validation.auth.local.email.invalid}")
    String email
) {}
