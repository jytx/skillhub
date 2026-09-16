package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.merge.AccountMergeRequestRepository;
import com.iflytek.skillhub.bootstrap.BootstrapAdminProperties;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserDeletionServiceTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final SkillRepository skillRepository = mock(SkillRepository.class);
    private final ReviewTaskRepository reviewTaskRepository = mock(ReviewTaskRepository.class);
    private final PromotionRequestRepository promotionRequestRepository = mock(PromotionRequestRepository.class);
    private final AccountMergeRequestRepository accountMergeRequestRepository = mock(AccountMergeRequestRepository.class);
    private final NamespaceMemberRepository namespaceMemberRepository = mock(NamespaceMemberRepository.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final UserOwnedDataCleaner ownedDataCleaner = mock(UserOwnedDataCleaner.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RequestIdAccessor requestIdAccessor = mock(RequestIdAccessor.class);

    private final BootstrapAdminProperties bootstrapAdminProperties = new BootstrapAdminProperties();

    private AdminUserDeletionService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserDeletionService(
                userAccountRepository,
                skillRepository,
                reviewTaskRepository,
                promotionRequestRepository,
                accountMergeRequestRepository,
                namespaceMemberRepository,
                namespaceRepository,
                bootstrapAdminProperties,
                ownedDataCleaner,
                auditLogService,
                requestIdAccessor);
        bootstrapAdminProperties.setUserId("docker-admin");
    }

    @Test
    void deleteUser_cleansOwnedDataAndEmptyPersonalNamespace() {
        UserAccount user = user("user-1");
        Namespace personal = namespace(10L, "user-1-ns", NamespaceType.TEAM);
        Namespace global = namespace(1L, "global", NamespaceType.GLOBAL);
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(skillRepository.findByOwnerId("user-1")).thenReturn(List.of());
        when(reviewTaskRepository.existsBySubmittedBy("user-1")).thenReturn(false);
        when(promotionRequestRepository.existsBySubmittedBy("user-1")).thenReturn(false);
        when(accountMergeRequestRepository.existsByPrimaryUserId("user-1")).thenReturn(false);
        when(accountMergeRequestRepository.existsBySecondaryUserId("user-1")).thenReturn(false);
        when(namespaceMemberRepository.findByUserId("user-1")).thenReturn(List.of(
                new NamespaceMember(10L, "user-1", NamespaceRole.OWNER),
                new NamespaceMember(1L, "user-1", NamespaceRole.MEMBER)));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(personal));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(global));
        when(skillRepository.existsByNamespaceId(10L)).thenReturn(false);
        when(reviewTaskRepository.existsByNamespaceId(10L)).thenReturn(false);
        when(promotionRequestRepository.existsByTargetNamespaceId(10L)).thenReturn(false);

        service.deleteUser("user-1", "admin-1", null);

        verify(ownedDataCleaner).cleanup("user-1");
        verify(namespaceMemberRepository).deleteByUserId("user-1");
        // 空的个人命名空间一并删除，GLOBAL 命名空间本身不动
        verify(namespaceRepository).delete(personal);
        verify(namespaceRepository, never()).delete(global);
        verify(userAccountRepository).delete(user);
        verify(auditLogService).record(eq("admin-1"), eq("ADMIN_USER_DELETE"), eq("USER"), isNull(),
                isNull(), isNull(), isNull(), anyString());
    }

    @Test
    void deleteUser_rejectsSystemAccount() {
        UserAccount systemUser = UserAccount.systemAccount("builtin-skill-publisher", "Publisher", null, null);
        when(userAccountRepository.findById("builtin-skill-publisher")).thenReturn(Optional.of(systemUser));

        assertThrows(DomainForbiddenException.class,
                () -> service.deleteUser("builtin-skill-publisher", "admin-1", null));

        verify(ownedDataCleaner, never()).cleanup(anyString());
        verify(userAccountRepository, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_rejectsBootstrapAdmin() {
        UserAccount admin = user("docker-admin");
        when(userAccountRepository.findById("docker-admin")).thenReturn(Optional.of(admin));

        assertThrows(DomainForbiddenException.class,
                () -> service.deleteUser("docker-admin", "admin-1", null));

        verify(userAccountRepository, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_rejectsSelfDeletion() {
        when(userAccountRepository.findById("admin-1")).thenReturn(Optional.of(user("admin-1")));

        assertThrows(DomainBadRequestException.class,
                () -> service.deleteUser("admin-1", "admin-1", null));

        verify(userAccountRepository, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_rejectsUserOwningSkills() {
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user("user-1")));
        when(skillRepository.findByOwnerId("user-1")).thenReturn(List.of(mock(Skill.class)));

        assertThrows(DomainBadRequestException.class,
                () -> service.deleteUser("user-1", "admin-1", null));

        verify(ownedDataCleaner, never()).cleanup(anyString());
        verify(userAccountRepository, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_rejectsUserWithReviewTasks() {
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user("user-1")));
        when(skillRepository.findByOwnerId("user-1")).thenReturn(List.of());
        when(reviewTaskRepository.existsBySubmittedBy("user-1")).thenReturn(true);

        assertThrows(DomainBadRequestException.class,
                () -> service.deleteUser("user-1", "admin-1", null));

        verify(userAccountRepository, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_rejectsUserWithPromotionRequests() {
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user("user-1")));
        when(skillRepository.findByOwnerId("user-1")).thenReturn(List.of());
        when(reviewTaskRepository.existsBySubmittedBy("user-1")).thenReturn(false);
        when(promotionRequestRepository.existsBySubmittedBy("user-1")).thenReturn(true);

        assertThrows(DomainBadRequestException.class,
                () -> service.deleteUser("user-1", "admin-1", null));

        verify(userAccountRepository, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_rejectsUserInMergeRequests() {
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user("user-1")));
        when(skillRepository.findByOwnerId("user-1")).thenReturn(List.of());
        when(reviewTaskRepository.existsBySubmittedBy("user-1")).thenReturn(false);
        when(promotionRequestRepository.existsBySubmittedBy("user-1")).thenReturn(false);
        when(accountMergeRequestRepository.existsByPrimaryUserId("user-1")).thenReturn(false);
        when(accountMergeRequestRepository.existsBySecondaryUserId("user-1")).thenReturn(true);

        assertThrows(DomainBadRequestException.class,
                () -> service.deleteUser("user-1", "admin-1", null));

        verify(userAccountRepository, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_rejectsWhenOwnedNamespaceIsNotEmpty() {
        Namespace personal = namespace(10L, "user-1-ns", NamespaceType.TEAM);
        when(userAccountRepository.findById("user-1")).thenReturn(Optional.of(user("user-1")));
        when(skillRepository.findByOwnerId("user-1")).thenReturn(List.of());
        when(reviewTaskRepository.existsBySubmittedBy("user-1")).thenReturn(false);
        when(promotionRequestRepository.existsBySubmittedBy("user-1")).thenReturn(false);
        when(accountMergeRequestRepository.existsByPrimaryUserId("user-1")).thenReturn(false);
        when(accountMergeRequestRepository.existsBySecondaryUserId("user-1")).thenReturn(false);
        when(namespaceMemberRepository.findByUserId("user-1"))
                .thenReturn(List.of(new NamespaceMember(10L, "user-1", NamespaceRole.OWNER)));
        when(namespaceRepository.findById(10L)).thenReturn(Optional.of(personal));
        when(skillRepository.existsByNamespaceId(10L)).thenReturn(true);

        assertThrows(DomainBadRequestException.class,
                () -> service.deleteUser("user-1", "admin-1", null));

        verify(namespaceRepository, never()).delete(isA(Namespace.class));
        verify(userAccountRepository, never()).delete(any(UserAccount.class));
    }

    @Test
    void deleteUser_withUnknownUser_throwsNotFound() {
        when(userAccountRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.deleteUser("missing", "admin-1", null));
    }

    private UserAccount user(String id) {
        UserAccount user = new UserAccount(id, "alice", "alice@example.com", null);
        user.setStatus(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-03-13T09:00:00Z"));
        ReflectionTestUtils.setField(user, "updatedAt", Instant.parse("2026-03-13T09:00:00Z"));
        return user;
    }

    private Namespace namespace(Long id, String slug, NamespaceType type) {
        Namespace namespace = new Namespace(slug, slug, null);
        namespace.setType(type);
        ReflectionTestUtils.setField(namespace, "id", id);
        return namespace;
    }
}
