package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalAccountRules;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.local.PasswordPolicyValidator;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.AdminUserImportParseResponse;
import com.iflytek.skillhub.dto.AdminUserImportRequest;
import com.iflytek.skillhub.dto.AdminUserImportResultResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理员 Excel 批量导入用户的应用服务。
 *
 * <p>两阶段：parse 解析上传的 .xlsx 并做行级预校验（格式、密码策略、库内与文件内重复），
 * 返回预览数据；importUsers 逐行调用注册服务创建账号。执行阶段不开启外层事务，
 * 每行注册独立提交，部分失败不影响已创建的账号。密码留空的行由服务端用
 * {@link SecureRandom} 生成随机初始密码，明文仅在结果响应中一次性返回。</p>
 */
@Service
public class AdminUserImportService {

    /** 单次导入的最大数据行数，防止误传大文件拖垮管理端 */
    static final int MAX_ROWS = 100;

    private static final Logger log = LoggerFactory.getLogger(AdminUserImportService.class);

    /** 随机密码长度：与前端创建用户弹窗的生成规则保持一致 */
    private static final int GENERATED_PASSWORD_LENGTH = 16;

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*-";

    private final LocalCredentialRepository localCredentialRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final LocalAuthService localAuthService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminUserImportService(LocalCredentialRepository localCredentialRepository,
                                  UserAccountRepository userAccountRepository,
                                  PasswordPolicyValidator passwordPolicyValidator,
                                  LocalAuthService localAuthService,
                                  AuditLogService auditLogService,
                                  RequestIdAccessor requestIdAccessor) {
        this.localCredentialRepository = localCredentialRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.localAuthService = localAuthService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    /** 解析上传的 Excel 并返回行级预览与校验结论，不做任何写入 */
    public AdminUserImportParseResponse parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainBadRequestException("error.admin.user.import.fileRequired");
        }
        List<AdminUserImportWorkbook.ImportRow> rawRows = AdminUserImportWorkbook.readRows(
                fileInputStreamOf(file));
        if (rawRows.isEmpty()) {
            throw new DomainBadRequestException("error.admin.user.import.noDataRows");
        }
        if (rawRows.size() > MAX_ROWS) {
            throw new DomainBadRequestException("error.admin.user.import.tooManyRows", MAX_ROWS);
        }
        return validateRows(rawRows);
    }

    /** 执行批量创建：逐行独立事务，失败行收集错误后继续，最后记审计 */
    public AdminUserImportResultResponse importUsers(AdminUserImportRequest request,
                                                     String actorUserId,
                                                     AuditRequestContext auditContext) {
        List<AdminUserImportResultResponse.RowResult> results = new ArrayList<>();
        int created = 0;
        for (AdminUserImportRequest.Row row : request.rows()) {
            boolean generated = row.password() == null || row.password().isBlank();
            String password = generated ? generatePassword() : row.password().trim();
            try {
                localAuthService.register(row.username(), password, row.email());
                created += 1;
                results.add(new AdminUserImportResultResponse.RowResult(
                        row.rowNumber(), row.username(), true, generated ? password : null, null));
            } catch (AuthFlowException ex) {
                results.add(new AdminUserImportResultResponse.RowResult(
                        row.rowNumber(), row.username(), false, null, ex.getMessageCode()));
            } catch (RuntimeException ex) {
                log.warn("User import failed unexpectedly for row {} ({})",
                        row.rowNumber(), row.username(), ex);
                results.add(new AdminUserImportResultResponse.RowResult(
                        row.rowNumber(), row.username(), false, null, "error.admin.user.import.rowFailed"));
            }
        }
        auditLogService.record(
                actorUserId,
                "ADMIN_USER_IMPORT",
                "USER",
                null,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                AuditDetail.builder()
                        .put("total", request.rows().size())
                        .put("created", created)
                        .put("failed", request.rows().size() - created)
                        .build());
        return new AdminUserImportResultResponse(results, created, results.size() - created);
    }

    /** 行级预校验：格式、密码策略、文件内重复与库内已存在（用户名/邮箱分别归一化比较） */
    private AdminUserImportParseResponse validateRows(List<AdminUserImportWorkbook.ImportRow> rawRows) {
        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<AdminUserImportParseResponse.Row> rows = new ArrayList<>(rawRows.size());
        int validCount = 0;
        for (AdminUserImportWorkbook.ImportRow raw : rawRows) {
            String username = LocalAccountRules.normalizeUsername(raw.username());
            String email = LocalAccountRules.normalizeEmail(raw.email());
            String errorKey = rowErrorKey(username, email, raw.password(), seenUsernames, seenEmails);
            if (errorKey == null) {
                validCount += 1;
                seenUsernames.add(username);
                seenEmails.add(email);
            }
            rows.add(new AdminUserImportParseResponse.Row(
                    raw.rowNumber(), username, email, raw.password(), errorKey == null, errorKey));
        }
        return new AdminUserImportParseResponse(rows, rows.size(), validCount, rows.size() - validCount);
    }

    private String rowErrorKey(String username, String email, String password,
                               Set<String> seenUsernames, Set<String> seenEmails) {
        if (username.isBlank()) {
            return "validation.auth.local.username.notBlank";
        }
        if (!LocalAccountRules.isUsernameValid(username)) {
            return "error.auth.local.username.invalid";
        }
        // 文件内重复检测：只有校验通过的行才登记入集合，错误行不参与冲突统计
        if (seenUsernames.contains(username)) {
            return "error.admin.user.import.duplicateRow";
        }
        if (email == null) {
            return "validation.auth.local.email.notBlank";
        }
        if (!LocalAccountRules.isEmailValid(email)) {
            return "validation.auth.local.email.invalid";
        }
        if (seenEmails.contains(email)) {
            return "error.admin.user.import.duplicateRow";
        }
        if (password != null && !password.isBlank()) {
            var errors = passwordPolicyValidator.validate(password);
            if (!errors.isEmpty()) {
                return errors.getFirst();
            }
        }
        if (localCredentialRepository.existsByUsernameIgnoreCase(username)) {
            return "error.auth.local.username.exists";
        }
        if (userAccountRepository.findByEmailIgnoreCase(email).isPresent()) {
            return "error.auth.local.email.exists";
        }
        return null;
    }

    /** 生成随机初始密码：大小写、数字、符号各至少一个后整体洗牌，天然满足密码策略 */
    private String generatePassword() {
        List<Character> chars = new ArrayList<>(GENERATED_PASSWORD_LENGTH);
        appendRandomFrom(UPPER, 1, chars);
        appendRandomFrom(LOWER, 1, chars);
        appendRandomFrom(DIGITS, 1, chars);
        appendRandomFrom(SYMBOLS, 1, chars);
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        appendRandomFrom(all, GENERATED_PASSWORD_LENGTH - chars.size(), chars);
        java.util.Collections.shuffle(chars, secureRandom);
        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        chars.forEach(password::append);
        return password.toString();
    }

    private void appendRandomFrom(String pool, int count, List<Character> target) {
        for (int i = 0; i < count; i++) {
            target.add(pool.charAt(secureRandom.nextInt(pool.length())));
        }
    }

    private java.io.InputStream fileInputStreamOf(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (java.io.IOException ex) {
            throw new DomainBadRequestException("error.admin.user.import.badFormat");
        }
    }
}
