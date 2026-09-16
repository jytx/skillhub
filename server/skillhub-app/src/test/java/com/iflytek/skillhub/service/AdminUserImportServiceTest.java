package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.local.PasswordPolicyValidator;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.AdminUserImportParseResponse;
import com.iflytek.skillhub.dto.AdminUserImportRequest;
import com.iflytek.skillhub.dto.AdminUserImportResultResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserImportServiceTest {

    private final LocalCredentialRepository localCredentialRepository = mock(LocalCredentialRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final LocalAuthService localAuthService = mock(LocalAuthService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RequestIdAccessor requestIdAccessor = mock(RequestIdAccessor.class);

    private final AdminUserImportService service = new AdminUserImportService(
            localCredentialRepository,
            userAccountRepository,
            new PasswordPolicyValidator(),
            localAuthService,
            auditLogService,
            requestIdAccessor);

    // ---------- parse ----------

    @Test
    void parse_validFile_returnsRowPreviewWithCounts() {
        upload(Optional.empty(), Optional.empty());
        AdminUserImportParseResponse response = service.parse(xlsxFile(List.of(
                new String[]{"username", "email", "password"},
                new String[]{"alice", "alice@example.com", "Abcd123!"},
                new String[]{"bob", "bob@example.com", null})));

        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.validCount()).isEqualTo(2);
        assertThat(response.invalidCount()).isZero();
        assertThat(response.rows().get(0).username()).isEqualTo("alice");
        // 密码列留空的行保留 null，执行阶段自动生成
        assertThat(response.rows().get(1).password()).isNull();
    }

    @Test
    void parse_normalizesUsernameAndEmailToLowerCase() {
        upload(Optional.empty(), Optional.empty());
        AdminUserImportParseResponse response = service.parse(xlsxFile(List.of(
                new String[]{"username", "email", "password"},
                new String[]{"  Alice ", "Alice@Example.COM", null})));

        assertThat(response.rows().get(0).username()).isEqualTo("alice");
        assertThat(response.rows().get(0).email()).isEqualTo("alice@example.com");
    }

    @Test
    void parse_supportsChineseHeaders() {
        upload(Optional.empty(), Optional.empty());
        AdminUserImportParseResponse response = service.parse(xlsxFile(List.of(
                new String[]{"用户名", "邮箱", "密码"},
                new String[]{"alice", "alice@example.com", null})));

        assertThat(response.validCount()).isEqualTo(1);
    }

    @Test
    void parse_missingRequiredHeaders_throwsBadRequest() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.parse(xlsxFile(List.of(
                        new String[]{"name", "mail"},
                        new String[]{"alice", "alice@example.com"}))));
        assertThat(ex.messageCode()).isEqualTo("error.admin.user.import.headerMissing");
    }

    @Test
    void parse_withoutDataRows_throwsBadRequest() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.parse(xlsxFile(List.<String[]>of(
                        new String[]{"username", "email", "password"}))));
        assertThat(ex.messageCode()).isEqualTo("error.admin.user.import.noDataRows");
    }

    @Test
    void parse_exceedingMaxRows_throwsBadRequest() {
        upload(Optional.empty(), Optional.empty());
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"username", "email", "password"});
        for (int i = 0; i <= AdminUserImportService.MAX_ROWS; i++) {
            rows.add(new String[]{"user" + i, "user" + i + "@example.com", null});
        }
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.parse(xlsxFile(rows)));
        assertThat(ex.messageCode()).isEqualTo("error.admin.user.import.tooManyRows");
    }

    @Test
    void parse_duplicateUsernameInsideFile_marksSecondRow() {
        upload(Optional.empty(), Optional.empty());
        AdminUserImportParseResponse response = service.parse(xlsxFile(List.of(
                new String[]{"username", "email", "password"},
                new String[]{"alice", "alice@example.com", null},
                new String[]{"alice", "alice2@example.com", null})));

        assertThat(response.rows().get(0).valid()).isTrue();
        assertThat(response.rows().get(1).valid()).isFalse();
        assertThat(response.rows().get(1).errorKey()).isEqualTo("error.admin.user.import.duplicateRow");
    }

    @Test
    void parse_existingUsernameInDatabase_marksRow() {
        upload(Optional.of("alice"), Optional.empty());
        AdminUserImportParseResponse response = service.parse(xlsxFile(List.of(
                new String[]{"username", "email", "password"},
                new String[]{"alice", "alice@example.com", null})));

        assertThat(response.rows().get(0).errorKey()).isEqualTo("error.auth.local.username.exists");
    }

    @Test
    void parse_existingEmailInDatabase_marksRow() {
        upload(Optional.empty(), Optional.of("alice@example.com"));
        AdminUserImportParseResponse response = service.parse(xlsxFile(List.of(
                new String[]{"username", "email", "password"},
                new String[]{"alice", "alice@example.com", null})));

        assertThat(response.rows().get(0).errorKey()).isEqualTo("error.auth.local.email.exists");
    }

    @Test
    void parse_invalidFormats_markRowWithLocalAuthErrorKeys() {
        upload(Optional.empty(), Optional.empty());
        AdminUserImportParseResponse response = service.parse(xlsxFile(List.of(
                new String[]{"username", "email", "password"},
                new String[]{"a", "alice@example.com", null},
                new String[]{"bob", "not-an-email", null},
                new String[]{"carol", "carol@example.com", "weak"})));

        assertThat(response.rows().get(0).errorKey()).isEqualTo("error.auth.local.username.invalid");
        assertThat(response.rows().get(1).errorKey()).isEqualTo("validation.auth.local.email.invalid");
        // "weak" 不足 8 位且字符类型不足，返回密码策略错误 key
        assertThat(response.rows().get(2).errorKey()).isEqualTo("error.auth.local.password.tooShort");
    }

    @Test
    void parse_skipsFullyEmptyRows() {
        upload(Optional.empty(), Optional.empty());
        AdminUserImportParseResponse response = service.parse(xlsxFile(List.of(
                new String[]{"username", "email", "password"},
                new String[]{null, null, null},
                new String[]{"alice", "alice@example.com", null})));

        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.rows().get(0).rowNumber()).isEqualTo(3);
    }

    @Test
    void parse_nonExcelContent_throwsBadFormat() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.parse(new MockMultipartFile(
                        "file", "users.xlsx", "application/octet-stream", "not excel".getBytes())));
        assertThat(ex.messageCode()).isEqualTo("error.admin.user.import.badFormat");
    }

    @Test
    void parse_emptyFile_throwsFileRequired() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                () -> service.parse(new MockMultipartFile("file", "users.xlsx", null, new byte[0])));
        assertThat(ex.messageCode()).isEqualTo("error.admin.user.import.fileRequired");
    }

    // ---------- importUsers ----------

    @Test
    void importUsers_generatesPasswordForBlankRowsAndReportsThem() {
        AdminUserImportResultResponse response = service.importUsers(request(
                row(2, "alice", null, "alice@example.com"),
                row(3, "bob", "Abcd123!", "bob@example.com")), "admin-1", null);

        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.failedCount()).isZero();
        // 留空行返回 16 位随机密码且满足策略，已填行不回传密码
        String generated = response.results().get(0).generatedPassword();
        assertThat(generated).hasSize(16);
        assertThat(generated).matches(p -> p.chars().anyMatch(Character::isUpperCase));
        assertThat(generated).matches(p -> p.chars().anyMatch(Character::isLowerCase));
        assertThat(generated).matches(p -> p.chars().anyMatch(Character::isDigit));
        assertThat(response.results().get(1).generatedPassword()).isNull();
        verify(localAuthService).register(eq("alice"), eq(generated), eq("alice@example.com"));
        verify(localAuthService).register(eq("bob"), eq("Abcd123!"), eq("bob@example.com"));
    }

    @Test
    void importUsers_collectsRowFailureAndContinues() {
        when(localAuthService.register(eq("alice"), anyString(), anyString()))
                .thenThrow(new AuthFlowException(HttpStatus.CONFLICT, "error.auth.local.username.exists"));

        AdminUserImportResultResponse response = service.importUsers(request(
                row(2, "alice", null, "alice@example.com"),
                row(3, "bob", null, "bob@example.com")), "admin-1", null);

        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results().get(0).success()).isFalse();
        assertThat(response.results().get(0).errorKey()).isEqualTo("error.auth.local.username.exists");
        assertThat(response.results().get(1).success()).isTrue();
    }

    @Test
    void importUsers_recordsAuditSummary() {
        service.importUsers(request(row(2, "alice", null, "alice@example.com")), "admin-1", null);

        verify(auditLogService).record(eq("admin-1"), eq("ADMIN_USER_IMPORT"), eq("USER"), isNull(),
                isNull(), isNull(), isNull(), contains("\"created\""));
    }

    @Test
    void importUsers_withUnexpectedError_reportsRowFailed() {
        when(localAuthService.register(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("boom"));

        AdminUserImportResultResponse response = service.importUsers(
                request(row(2, "alice", null, "alice@example.com")), "admin-1", null);

        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results().get(0).errorKey()).isEqualTo("error.admin.user.import.rowFailed");
    }

    // ---------- 模板 ----------

    @Test
    void template_roundTripsThroughReader() {
        byte[] template = AdminUserImportWorkbook.templateBytes();
        assertThat(template).isNotEmpty();

        List<AdminUserImportWorkbook.ImportRow> rows =
                AdminUserImportWorkbook.readRows(new ByteArrayInputStream(template));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).username()).isEqualTo("alice");
        assertThat(rows.get(0).email()).isEqualTo("alice@example.com");
        assertThat(rows.get(0).password()).isNull();
    }

    // ---------- helpers ----------

    /** DB 查重桩：默认用户名与邮箱都不存在；传入已存在的用户名/邮箱时按条返回命中 */
    private void upload(Optional<String> existingUsername, Optional<String> existingEmail) {
        when(localCredentialRepository.existsByUsernameIgnoreCase(anyString()))
                .thenReturn(false);
        existingUsername.ifPresent(username ->
                when(localCredentialRepository.existsByUsernameIgnoreCase(username)).thenReturn(true));
        when(userAccountRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(Optional.empty());
        existingEmail.ifPresent(email -> {
            UserAccount user = new UserAccount("user-x", "someone", email, null);
            when(userAccountRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
        });
    }

    private AdminUserImportRequest request(AdminUserImportRequest.Row... rows) {
        return new AdminUserImportRequest(List.of(rows));
    }

    private AdminUserImportRequest.Row row(int rowNumber, String username, String password, String email) {
        return new AdminUserImportRequest.Row(rowNumber, username, password, email);
    }

    /** 用 POI 在测试内构造 .xlsx 字节；null 单元格留空 */
    private MockMultipartFile xlsxFile(List<String[]> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("users");
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i);
                String[] values = rows.get(i);
                for (int c = 0; c < values.length; c++) {
                    if (values[c] != null) {
                        row.createCell(c).setCellValue(values[c]);
                    }
                }
            }
            workbook.write(out);
            return new MockMultipartFile("file", "users.xlsx", null, out.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build test workbook", ex);
        }
    }
}
