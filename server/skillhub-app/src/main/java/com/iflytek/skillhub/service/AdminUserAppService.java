package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.bootstrap.BootstrapAdminProperties;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.event.UserActivatedEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.AdminUserMutationResponse;
import com.iflytek.skillhub.dto.AdminUserSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.AdminUserSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administrative user-management application service built around the main
 * search and mutation use cases exposed by the admin API.
 */
@Service
public class AdminUserAppService {

    private static final Set<UserStatus> MANAGEABLE_STATUSES = Set.of(UserStatus.ACTIVE, UserStatus.DISABLED);
    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String USER_ROLE = "USER";

    private final AdminUserSearchRepository adminUserSearchRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LocalAuthService localAuthService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final BootstrapAdminProperties bootstrapAdminProperties;

    public AdminUserAppService(
            AdminUserSearchRepository adminUserSearchRepository,
            UserAccountRepository userAccountRepository,
            UserRoleBindingRepository userRoleBindingRepository,
            RoleRepository roleRepository,
            ApplicationEventPublisher eventPublisher,
            LocalAuthService localAuthService,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor,
            BootstrapAdminProperties bootstrapAdminProperties) {
        this.adminUserSearchRepository = adminUserSearchRepository;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
        this.localAuthService = localAuthService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.bootstrapAdminProperties = bootstrapAdminProperties;
    }

    /**
     * 管理员创建本地账号。复用注册逻辑以保证账号、凭据、全局命名空间成员、
     * 个人命名空间等副作用与自助注册完全一致；新账号默认 USER 角色，
     * 如需更高权限由管理员在创建后通过角色变更授予。
     */
    @Transactional
    public AdminUserSummaryResponse createUser(String username, String password, String email) {
        PlatformPrincipal principal = localAuthService.register(username, password, email);
        UserAccount user = loadUser(principal.userId());
        List<String> roles = loadRolesByUserId(List.of(user.getId())).getOrDefault(user.getId(), List.of());
        return toSummary(user, roles);
    }

    /**
     * 管理员编辑用户资料，仅允许修改显示名与邮箱。系统账号不可编辑；
     * 邮箱按忽略大小写查重，排除用户自身后不得与其他账号重复。
     */
    @Transactional
    public AdminUserSummaryResponse updateUser(String userId,
                                               String displayName,
                                               String email,
                                               String actorUserId,
                                               AuditRequestContext auditContext) {
        UserAccount user = loadUser(userId);
        rejectSystemAccountMutation(user);
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        userAccountRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new DomainConflictException("error.admin.user.email.exists", normalizedEmail);
                });
        user.setDisplayName(displayName == null ? null : displayName.trim());
        user.setEmail(normalizedEmail);
        UserAccount saved = userAccountRepository.save(user);
        recordAudit("ADMIN_USER_UPDATE", actorUserId, saved, auditContext);
        List<String> roles = loadRolesByUserId(List.of(saved.getId())).getOrDefault(saved.getId(), List.of());
        return toSummary(saved, roles);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> listUsers(String search, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserAccount> result = adminUserSearchRepository.search(
                search,
                StringUtils.hasText(status) ? parseStatus(status) : null,
                pageable
        );
        Map<String, List<String>> rolesByUserId = loadRolesByUserId(
                result.getContent().stream().map(UserAccount::getId).toList());

        List<AdminUserSummaryResponse> items = result.getContent().stream()
                .map(user -> toSummary(
                        user,
                        rolesByUserId.getOrDefault(user.getId(), List.of())))
                .toList();

        return new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional
    public AdminUserMutationResponse updateUserRole(String userId, String roleCode, Set<String> actorPlatformRoles) {
        UserAccount user = loadUser(userId);
        rejectSystemAccountMutation(user);
        String normalizedRoleCode = normalizeRoleCode(roleCode);
        boolean targetHasSuperAdminRole = userRoleBindingRepository.findByUserId(user.getId()).stream()
                .anyMatch(binding -> SUPER_ADMIN_ROLE.equals(binding.getRole().getCode()));

        if ((SUPER_ADMIN_ROLE.equals(normalizedRoleCode) || targetHasSuperAdminRole)
                && (actorPlatformRoles == null || !actorPlatformRoles.contains(SUPER_ADMIN_ROLE))) {
            throw new DomainForbiddenException("error.admin.user.role.superAdmin.assignDenied");
        }

        userRoleBindingRepository.deleteByUserId(user.getId());

        if (!USER_ROLE.equals(normalizedRoleCode)) {
            Role role = roleRepository.findByCode(normalizedRoleCode)
                    .orElseThrow(() -> new DomainBadRequestException("error.admin.user.role.invalid", roleCode));
            userRoleBindingRepository.save(new UserRoleBinding(user.getId(), role));
        }

        return new AdminUserMutationResponse(user.getId(), normalizedRoleCode, user.getStatus().name());
    }

    @Transactional
    public AdminUserMutationResponse updateUserStatus(String userId, String status) {
        UserAccount user = loadUser(userId);
        rejectSystemAccountMutation(user);
        UserStatus nextStatus = parseManageableStatus(status);
        UserStatus previousStatus = user.getStatus();
        user.setStatus(nextStatus);
        userAccountRepository.save(user);
        if (nextStatus == UserStatus.ACTIVE && previousStatus != UserStatus.ACTIVE) {
            eventPublisher.publishEvent(
                    new UserActivatedEvent(user.getId(), user.getDisplayName(), user.getEmail()));
        }
        return new AdminUserMutationResponse(user.getId(), null, nextStatus.name());
    }

    private UserStatus parseManageableStatus(String status) {
        UserStatus parsedStatus = parseStatus(status);
        if (!MANAGEABLE_STATUSES.contains(parsedStatus)) {
            throw new DomainBadRequestException("error.admin.user.status.unsupported");
        }
        return parsedStatus;
    }

    private UserStatus parseStatus(String status) {
        try {
            return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.admin.user.status.invalid", status);
        }
    }

    private String normalizeRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new DomainBadRequestException("error.admin.user.role.invalid", roleCode);
        }
        return roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, List<String>> loadRolesByUserId(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> explicitRolesByUserId = userRoleBindingRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        UserRoleBinding::getUserId,
                        Collectors.mapping(binding -> binding.getRole().getCode(),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        roles -> roles.stream().sorted().toList()))));
        return userIds.stream().collect(Collectors.toMap(
                userId -> userId,
                userId -> withDefaultUserRole(explicitRolesByUserId.getOrDefault(userId, List.of())).stream()
                        .sorted()
                        .toList()
        ));
    }

    private Set<String> withDefaultUserRole(List<String> roles) {
        Set<String> resolvedRoles = new TreeSet<>();
        if (roles != null) {
            resolvedRoles.addAll(roles);
        }
        if (resolvedRoles.isEmpty()) {
            resolvedRoles.add("USER");
        }
        return Set.copyOf(resolvedRoles);
    }

    private UserAccount loadUser(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new DomainNotFoundException("error.admin.user.notFound", userId));
    }

    private AdminUserSummaryResponse toSummary(UserAccount user, List<String> roles) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getStatus().name(),
                roles,
                user.getCreatedAt(),
                user.isSystemAccount(),
                isDeletable(user));
    }

    /** 删除保护的后端统一判定：内置系统账号与引导管理员账号不可删除 */
    private boolean isDeletable(UserAccount user) {
        return !user.isSystemAccount()
                && !Objects.equals(user.getId(), bootstrapAdminProperties.getUserId());
    }

    /**
     * 用户管理操作的审计记录。用户 id 是字符串而 audit_log.target_id 是长整型，
     * 因此 targetId 传 null、把目标用户信息放进 detailJson。
     */
    private void recordAudit(String action, String actorUserId, UserAccount targetUser, AuditRequestContext auditContext) {
        auditLogService.record(
                actorUserId,
                action,
                "USER",
                null,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                AuditDetail.of("userId", targetUser.getId(), "username", targetUser.getDisplayName()));
    }

    private void rejectSystemAccountMutation(UserAccount user) {
        if (user.isSystemAccount()) {
            throw new DomainForbiddenException("error.admin.user.systemAccount.immutable");
        }
    }
}
