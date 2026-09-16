package com.iflytek.skillhub.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.metrics.SkillHubMetrics;
import com.iflytek.skillhub.security.AuthFailureThrottleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 验证关闭自助注册开关后注册接口的行为。
 */
@SpringBootTest(properties = "skillhub.auth.local.registration-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalAuthRegistrationDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalAuthService localAuthService;

    @MockBean
    private LocalCredentialRepository localCredentialRepository;

    @MockBean
    private SkillHubMetrics skillHubMetrics;

    @MockBean
    private AuthFailureThrottleService authFailureThrottleService;

    @Test
    void register_returnsForbiddenWhenRegistrationDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/local/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"bob","password":"Abcd123!","email":"bob@example.com"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        // 开关关闭时注册服务不应被触达，也不应计入注册指标
        verify(localAuthService, never()).register("bob", "Abcd123!", "bob@example.com");
        verify(skillHubMetrics, never()).incrementUserRegister();
    }
}
