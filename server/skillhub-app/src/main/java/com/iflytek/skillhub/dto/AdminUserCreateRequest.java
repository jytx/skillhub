package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 管理员后台创建本地账号的请求体。
 *
 * <p>校验规则与自助注册保持一致，密码强度等业务规则由注册服务统一校验。</p>
 */
public record AdminUserCreateRequest(
    @NotBlank(message = "{validation.auth.local.username.notBlank}")
    String username,
    @NotBlank(message = "{validation.auth.local.password.notBlank}")
    String password,
    @NotBlank(message = "{validation.auth.local.email.notBlank}")
    @Email(message = "{validation.auth.local.email.invalid}")
    String email
) {}
