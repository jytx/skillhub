package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.dto.AdminUserImportParseResponse;
import com.iflytek.skillhub.dto.AdminUserImportRequest;
import com.iflytek.skillhub.dto.AdminUserImportResultResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.exception.UnauthorizedException;
import com.iflytek.skillhub.service.AdminUserImportService;
import com.iflytek.skillhub.service.AdminUserImportWorkbook;
import com.iflytek.skillhub.service.AuditRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理员 Excel 批量导入用户的端点：模板下载、上传解析预览、确认执行导入。
 */
@RestController
@RequestMapping("/api/v1/admin/users/import")
public class AdminUserImportController extends BaseApiController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final AdminUserImportService adminUserImportService;

    public AdminUserImportController(AdminUserImportService adminUserImportService,
                                     ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminUserImportService = adminUserImportService;
    }

    /** 下载导入模板（username/email/password 三列表头 + 示例行），直接返回二进制文件 */
    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] template = AdminUserImportWorkbook.templateBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + AdminUserImportWorkbook.TEMPLATE_FILENAME + "\"")
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .contentLength(template.length)
                .body(template);
    }

    /** 上传 .xlsx 并解析校验，返回行级预览数据，不执行任何写入 */
    @PostMapping("/parse")
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserImportParseResponse> parse(
            @RequestParam("file") MultipartFile file) {
        return ok("response.success.read", adminUserImportService.parse(file));
    }

    /** 确认执行批量创建；密码留空的行由服务端生成随机初始密码并在结果中一次性返回 */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminUserImportResultResponse> importUsers(
            @Valid @RequestBody AdminUserImportRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        if (principal == null) {
            throw new UnauthorizedException("error.auth.required");
        }
        return ok("response.success.created",
                adminUserImportService.importUsers(request, principal.userId(),
                        AuditRequestContext.from(httpRequest)));
    }
}
