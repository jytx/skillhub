package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.AdminUserImportParseResponse;
import com.iflytek.skillhub.dto.AdminUserImportRequest;
import com.iflytek.skillhub.dto.AdminUserImportResultResponse;
import com.iflytek.skillhub.service.AdminUserImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AdminUserImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminUserImportService adminUserImportService;

    @MockBean
    private DeviceAuthService deviceAuthService;

    private UsernamePasswordAuthenticationToken userAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "admin", "admin@example.com", "", "github", Set.of("USER_ADMIN"));
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN")));
    }

    @Test
    void downloadTemplate_withUserAdminRole_returnsWorkbook() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/import/template")
                        .with(authentication(userAdminAuth())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"skillhub-users-template.xlsx\""));
    }

    @Test
    void parse_withUserAdminRole_returnsPreview() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "users.xlsx", null, "stub".getBytes());
        when(adminUserImportService.parse(file)).thenReturn(new AdminUserImportParseResponse(
                List.of(new AdminUserImportParseResponse.Row(
                        2, "alice", "alice@example.com", null, true, null)),
                1, 1, 0));

        mockMvc.perform(multipart("/api/v1/admin/users/import/parse")
                        .file(file)
                        .with(authentication(userAdminAuth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.validCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].username").value("alice"));
    }

    @Test
    void parse_withoutAdminRole_returns403() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-77", "skilladmin", "skilladmin@example.com", "", "github", Set.of("SKILL_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));

        mockMvc.perform(multipart("/api/v1/admin/users/import/parse")
                        .file(new MockMultipartFile("file", "users.xlsx", null, "stub".getBytes()))
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void importUsers_withUserAdminRole_delegatesAndReturnsResult() throws Exception {
        when(adminUserImportService.importUsers(any(AdminUserImportRequest.class), eq("user-42"), any()))
                .thenReturn(new AdminUserImportResultResponse(
                        List.of(new AdminUserImportResultResponse.RowResult(
                                2, "alice", true, "Generated!Pass1@", null)),
                        1, 0));

        mockMvc.perform(post("/api/v1/admin/users/import")
                        .with(authentication(userAdminAuth()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"rows":[{"rowNumber":2,"username":"alice","email":"alice@example.com"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.results[0].generatedPassword").value("Generated!Pass1@"));

        verify(adminUserImportService).importUsers(any(AdminUserImportRequest.class), eq("user-42"), any());
    }

    @Test
    void importUsers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/import")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"rows":[{"rowNumber":2,"username":"alice","email":"alice@example.com"}]}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
