package com.iflytek.skillhub.auth.local;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 本地账号（用户名 + 密码）的公共校验规则与归一化工具。
 *
 * <p>注册/登录（{@link LocalAuthService}）与管理员批量导入预校验必须使用同一套规则，
 * 统一放在这里避免两处复制导致的行为漂移。规则内容：</p>
 * <ul>
 *   <li>用户名：3-64 位字母、数字或下划线，注册与比较前统一 trim + 转小写</li>
 *   <li>邮箱：常见邮箱格式，归一化 trim + 转小写，空白输入归为 null（邮箱必填的判断由调用方负责）</li>
 * </ul>
 */
public final class LocalAccountRules {

    /** 用户名格式：3-64 位字母、数字或下划线 */
    public static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,64}$");

    /** 邮箱格式：与前端 local-account-rules.ts 保持一致 */
    public static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private LocalAccountRules() {
    }

    /** 用户名归一化：trim + 转小写；null 归为空串 */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** 邮箱归一化：trim + 转小写；空白归为 null（表示未填写） */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** 用户名是否满足格式规则（不做查重） */
    public static boolean isUsernameValid(String normalizedUsername) {
        return USERNAME_PATTERN.matcher(normalizedUsername).matches();
    }

    /** 邮箱是否满足格式规则（不做查重，null 视为无效） */
    public static boolean isEmailValid(String normalizedEmail) {
        return normalizedEmail != null && EMAIL_PATTERN.matcher(normalizedEmail).matches();
    }
}
