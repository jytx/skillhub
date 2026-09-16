package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地账号自助注册开关。
 *
 * <p>配置项：{@code skillhub.auth.local.registration-enabled}（环境变量
 * {@code SKILLHUB_AUTH_LOCAL_REGISTRATION_ENABLED}）。默认开启以保持开源版行为不变；
 * 企业内网部署可关闭自助注册，改由管理员通过后台接口创建账号。</p>
 */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.local")
public class LocalAuthRegistrationProperties {

    /**
     * 是否开放自助注册；关闭后 /api/v1/auth/local/register 返回 403。
     */
    private boolean registrationEnabled = true;

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }
}
