package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.merge.AccountMergeRequestRepository;
import com.iflytek.skillhub.bootstrap.BootstrapAdminProperties;
import com.iflytek.skillhub.domain.audit.AuditDetail;
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
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 管理员删除用户的应用服务。
 *
 * <p>删除策略：仅允许删除没有任何业务产出的账号——名下有技能、审核任务、
 * 晋级请求或合并请求的用户需先处理这些数据；个人从属数据由
 * {@link UserOwnedDataCleaner} 清理；空的个人命名空间一并删除，非空的拒绝删除。
 * 内置系统账号、引导管理员账号与操作者本人任何情况下不可删除。</p>
 */
@Service
public class AdminUserDeletionService {

    private final UserAccountRepository userAccountRepository;
    private final SkillRepository skillRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final PromotionRequestRepository promotionRequestRepository;
    private final AccountMergeRequestRepository accountMergeRequestRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final NamespaceRepository namespaceRepository;
    private final BootstrapAdminProperties bootstrapAdminProperties;
    private final UserOwnedDataCleaner ownedDataCleaner;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;

    public AdminUserDeletionService(UserAccountRepository userAccountRepository,
                                    SkillRepository skillRepository,
                                    ReviewTaskRepository reviewTaskRepository,
                                    PromotionRequestRepository promotionRequestRepository,
                                    AccountMergeRequestRepository accountMergeRequestRepository,
                                    NamespaceMemberRepository namespaceMemberRepository,
                                    NamespaceRepository namespaceRepository,
                                    BootstrapAdminProperties bootstrapAdminProperties,
                                    UserOwnedDataCleaner ownedDataCleaner,
                                    AuditLogService auditLogService,
                                    RequestIdAccessor requestIdAccessor) {
        this.userAccountRepository = userAccountRepository;
        this.skillRepository = skillRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.promotionRequestRepository = promotionRequestRepository;
        this.accountMergeRequestRepository = accountMergeRequestRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.namespaceRepository = namespaceRepository;
        this.bootstrapAdminProperties = bootstrapAdminProperties;
        this.ownedDataCleaner = ownedDataCleaner;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
    }

    /**
     * 删除用户。先完成全部保护与关联数据检查（任一不满足即抛出、不做部分删除），
     * 再清理从属数据、命名空间成员关系与空命名空间，最后删除账号本体并记审计。
     */
    @Transactional
    public void deleteUser(String userId, String actorUserId, AuditRequestContext auditContext) {
        UserAccount user = loadUser(userId);
        rejectProtectedAccount(user, actorUserId);
        rejectUsersWithBusinessData(userId);
        List<Namespace> deletableNamespaces = resolveDeletableNamespaces(userId);

        ownedDataCleaner.cleanup(userId);
        namespaceMemberRepository.deleteByUserId(userId);
        deletableNamespaces.forEach(namespaceRepository::delete);
        userAccountRepository.delete(user);

        auditLogService.record(
                actorUserId,
                "ADMIN_USER_DELETE",
                "USER",
                null,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                AuditDetail.of("userId", user.getId(), "username", user.getDisplayName()));
    }

    private UserAccount loadUser(String userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new DomainNotFoundException("error.admin.user.notFound", userId));
    }

    /** 三类账号不可删除：内置系统账号、引导管理员账号、操作者本人。 */
    private void rejectProtectedAccount(UserAccount user, String actorUserId) {
        if (user.isSystemAccount()) {
            throw new DomainForbiddenException("error.admin.user.systemAccount.immutable");
        }
        if (Objects.equals(user.getId(), bootstrapAdminProperties.getUserId())) {
            throw new DomainForbiddenException("error.admin.user.delete.protected");
        }
        if (Objects.equals(user.getId(), actorUserId)) {
            throw new DomainBadRequestException("error.admin.user.delete.self");
        }
    }

    /** 名下存在业务产出（技能/审核任务/晋级请求/合并请求）时拒绝删除。 */
    private void rejectUsersWithBusinessData(String userId) {
        if (!skillRepository.findByOwnerId(userId).isEmpty()) {
            throw new DomainBadRequestException("error.admin.user.delete.hasSkills");
        }
        if (reviewTaskRepository.existsBySubmittedBy(userId)) {
            throw new DomainBadRequestException("error.admin.user.delete.hasReviewTasks");
        }
        if (promotionRequestRepository.existsBySubmittedBy(userId)) {
            throw new DomainBadRequestException("error.admin.user.delete.hasPromotionRequests");
        }
        if (accountMergeRequestRepository.existsByPrimaryUserId(userId)
                || accountMergeRequestRepository.existsBySecondaryUserId(userId)) {
            throw new DomainBadRequestException("error.admin.user.delete.hasMergeRequests");
        }
    }

    /**
     * 找出该用户可作为 OWNER 一并删除的空命名空间（非 GLOBAL，且无技能、
     * 无审核任务、无晋级引用）。只要存在一个非空的 OWNED 命名空间即拒绝删除。
     */
    private List<Namespace> resolveDeletableNamespaces(String userId) {
        List<Namespace> deletable = new ArrayList<>();
        for (NamespaceMember member : namespaceMemberRepository.findByUserId(userId)) {
            if (member.getRole() != NamespaceRole.OWNER) {
                continue;
            }
            Optional<Namespace> namespace = namespaceRepository.findById(member.getNamespaceId());
            if (namespace.isEmpty() || namespace.get().getType() == NamespaceType.GLOBAL) {
                continue;
            }
            if (namespaceIsEmpty(namespace.get())) {
                deletable.add(namespace.get());
            } else {
                throw new DomainBadRequestException(
                        "error.admin.user.delete.namespaceNotEmpty", namespace.get().getSlug());
            }
        }
        return deletable;
    }

    private boolean namespaceIsEmpty(Namespace namespace) {
        Long namespaceId = namespace.getId();
        return !skillRepository.existsByNamespaceId(namespaceId)
                && !reviewTaskRepository.existsByNamespaceId(namespaceId)
                && !promotionRequestRepository.existsByTargetNamespaceId(namespaceId);
    }
}
