package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.entity.Role;
import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.local.LocalAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.repository.RoleRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.AdminUserSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class AdminUserAppServiceTest {

    private final AdminUserSearchRepository adminUserSearchRepository = mock(AdminUserSearchRepository.class);
    private final UserRoleBindingRepository userRoleBindingRepository = mock(UserRoleBindingRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final LocalAuthService localAuthService = mock(LocalAuthService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RequestIdAccessor requestIdAccessor = mock(RequestIdAccessor.class);
    private final AdminUserAppService service = new AdminUserAppService(
            adminUserSearchRepository,
            userAccountRepository,
            userRoleBindingRepository,
            roleRepository,
            eventPublisher,
            localAuthService,
            auditLogService,
            requestIdAccessor
    );

    @Test
    void createUser_reusesRegistrationAndReturnsSummary() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-9", "dave", "dave@example.com", null, "local", Set.of());
        when(localAuthService.register("dave", "Abcd123!", "dave@example.com")).thenReturn(principal);
        when(userAccountRepository.findById("user-9"))
                .thenReturn(Optional.of(user("user-9", "dave", "dave@example.com", UserStatus.ACTIVE)));
        when(userRoleBindingRepository.findByUserIdIn(List.of("user-9"))).thenReturn(List.of());

        var response = service.createUser("dave", "Abcd123!", "dave@example.com");

        assertThat(response.id()).isEqualTo("user-9");
        assertThat(response.username()).isEqualTo("dave");
        assertThat(response.status()).isEqualTo("ACTIVE");
        // 无显式角色绑定时回落到默认 USER 角色，与自助注册行为一致
        assertThat(response.platformRoles()).isEqualTo(List.of("USER"));
    }

    @Test
    void createUser_propagatesRegistrationConflicts() {
        when(localAuthService.register("alice", "Abcd123!", "alice@example.com"))
                .thenThrow(new AuthFlowException(HttpStatus.CONFLICT, "error.auth.local.username.exists"));

        assertThrows(AuthFlowException.class,
                () -> service.createUser("alice", "Abcd123!", "alice@example.com"));
    }

    @Test
    void listUsers_returnsPagedUsersFromRepository() {
        UserAccount user = user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(adminUserSearchRepository.search("ali", UserStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(userRoleBindingRepository.findByUserIdIn(List.of("user-1")))
                .thenReturn(List.of(new UserRoleBinding("user-1", role("AUDITOR"))));

        PageResponse<?> response = service.listUsers("ali", "ACTIVE", 0, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0)).extracting("id", "username", "email", "status")
                .containsExactly("user-1", "alice", "alice@example.com", "ACTIVE");
        assertThat(response.items().get(0)).extracting("platformRoles")
                .isEqualTo(List.of("AUDITOR"));
    }

    @Test
    void listUsers_defaultsToUserRoleWhenNoExplicitBindingExists() {
        UserAccount user = user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE);
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(adminUserSearchRepository.search(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(userRoleBindingRepository.findByUserIdIn(List.of("user-1"))).thenReturn(List.of());

        PageResponse<?> response = service.listUsers(null, null, 0, 20);

        assertThat(response.items().get(0)).extracting("platformRoles")
                .isEqualTo(List.of("USER"));
    }

    @Test
    void listUsers_withInvalidStatus_throwsBadRequest() {
        assertThrows(DomainBadRequestException.class, () -> service.listUsers(null, "BANNED", 0, 20));
    }

    @Test
    void updateUserRole_nonSuperAdminCannotAssignSuperAdmin() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));

        assertThrows(DomainForbiddenException.class,
                () -> service.updateUserRole("user-1", "SUPER_ADMIN", Set.of("USER_ADMIN")));
    }

    @Test
    void updateUserRole_nonSuperAdminCannotReplaceExistingSuperAdminRole() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));
        when(userRoleBindingRepository.findByUserId("user-1"))
                .thenReturn(List.of(new UserRoleBinding("user-1", role("SUPER_ADMIN"))));

        assertThrows(DomainForbiddenException.class,
                () -> service.updateUserRole("user-1", "USER", Set.of("USER_ADMIN")));

        verify(userRoleBindingRepository, never()).deleteByUserId(any());
        verify(userRoleBindingRepository, never()).save(any(UserRoleBinding.class));
    }

    @Test
    void updateUserRole_rejectsSystemAccount() {
        when(userAccountRepository.findById("builtin-skill-publisher"))
                .thenReturn(Optional.of(systemUser()));

        assertThrows(DomainForbiddenException.class,
                () -> service.updateUserRole("builtin-skill-publisher", "AUDITOR", Set.of("SUPER_ADMIN")));

        verify(userRoleBindingRepository, never()).deleteByUserId(any());
        verify(userRoleBindingRepository, never()).save(any(UserRoleBinding.class));
    }

    @Test
    void updateUserRole_replacesExistingBindings() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));
        when(roleRepository.findByCode("AUDITOR")).thenReturn(Optional.of(role("AUDITOR")));

        var response = service.updateUserRole("user-1", "AUDITOR", Set.of("SUPER_ADMIN"));

        verify(userRoleBindingRepository).deleteByUserId("user-1");
        verify(userRoleBindingRepository).save(any(UserRoleBinding.class));
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.role()).isEqualTo("AUDITOR");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void updateUserRole_userPseudoRoleClearsBindingsWithoutSavingNewRole() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));

        var response = service.updateUserRole("user-1", "USER", Set.of("SUPER_ADMIN"));

        verify(userRoleBindingRepository).deleteByUserId("user-1");
        verify(userRoleBindingRepository, never()).save(any(UserRoleBinding.class));
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void updateUserStatus_rejectsUnsupportedStatuses() {
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));

        assertThrows(DomainBadRequestException.class, () -> service.updateUserStatus("user-1", "MERGED"));
    }

    @Test
    void updateUserStatus_updatesPersistedStatus() {
        UserAccount user = user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE);
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userAccountRepository.save(user)).thenReturn(user);

        var response = service.updateUserStatus("user-1", "DISABLED");

        verify(userAccountRepository).save(user);
        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(response.status()).isEqualTo("DISABLED");
    }

    @Test
    void updateUserStatus_rejectsSystemAccount() {
        when(userAccountRepository.findById("builtin-skill-publisher"))
                .thenReturn(Optional.of(systemUser()));

        assertThrows(DomainForbiddenException.class,
                () -> service.updateUserStatus("builtin-skill-publisher", "DISABLED"));

        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void updateUserStatus_withUnknownUser_throwsNotFound() {
        when(userAccountRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service.updateUserStatus("missing", "DISABLED"));
    }

    @Test
    void updateUser_updatesDisplayNameAndEmail() {
        UserAccount user = user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE);
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userAccountRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.save(user)).thenReturn(user);
        when(userRoleBindingRepository.findByUserIdIn(List.of("user-1"))).thenReturn(List.of());

        var response = service.updateUser(
                "user-1", "alice_new", "New@Example.com", "admin-1", null);

        verify(userAccountRepository).save(user);
        // 邮箱按忽略大小写归一化后保存，显示名去除首尾空白
        assertThat(user.getDisplayName()).isEqualTo("alice_new");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(response.username()).isEqualTo("alice_new");
        assertThat(response.email()).isEqualTo("new@example.com");
        verify(auditLogService).record(eq("admin-1"), eq("ADMIN_USER_UPDATE"), eq("USER"), isNull(),
                isNull(), isNull(), isNull(), anyString());
    }

    @Test
    void updateUser_keepsEmailWhenItBelongsToTheSameUser() {
        UserAccount user = user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE);
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        // 查重命中的是用户自己的记录，不应视为冲突
        when(userAccountRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(userAccountRepository.save(user)).thenReturn(user);
        when(userRoleBindingRepository.findByUserIdIn(List.of("user-1"))).thenReturn(List.of());

        var response = service.updateUser(
                "user-1", "alice", "alice@example.com", "admin-1", null);

        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void updateUser_withEmailOwnedByAnotherUser_throwsConflict() {
        UserAccount other = user("user-2", "bob", "bob@example.com", UserStatus.ACTIVE);
        when(userAccountRepository.findById("user-1"))
                .thenReturn(Optional.of(user("user-1", "alice", "alice@example.com", UserStatus.ACTIVE)));
        when(userAccountRepository.findByEmailIgnoreCase("bob@example.com")).thenReturn(Optional.of(other));

        assertThrows(DomainConflictException.class,
                () -> service.updateUser("user-1", "alice", "bob@example.com", "admin-1", null));

        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void updateUser_rejectsSystemAccount() {
        when(userAccountRepository.findById("builtin-skill-publisher"))
                .thenReturn(Optional.of(systemUser()));

        assertThrows(DomainForbiddenException.class,
                () -> service.updateUser(
                        "builtin-skill-publisher", "renamed", "renamed@example.com", "admin-1", null));

        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    @Test
    void updateUser_withUnknownUser_throwsNotFound() {
        when(userAccountRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.updateUser("missing", "alice", "alice@example.com", "admin-1", null));
    }

    private UserAccount user(String id, String displayName, String email, UserStatus status) {
        UserAccount user = new UserAccount(id, displayName, email, null);
        user.setStatus(status);
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-03-13T09:00:00Z"));
        ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-03-13T09:00:00Z"));
        return user;
    }

    private UserAccount systemUser() {
        UserAccount user = UserAccount.systemAccount(
                "builtin-skill-publisher",
                "Built-in Skill Publisher",
                null,
                null
        );
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-03-13T09:00:00Z"));
        ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-03-13T09:00:00Z"));
        return user;
    }

    private Role role(String code) {
        Role role = new Role();
        ReflectionTestUtils.setField(role, "code", code);
        ReflectionTestUtils.setField(role, "name", code);
        return role;
    }
}
