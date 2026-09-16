package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.governance.UserNotificationRepository;
import com.iflytek.skillhub.domain.social.SkillRating;
import com.iflytek.skillhub.domain.social.SkillRatingRepository;
import com.iflytek.skillhub.domain.social.SkillStar;
import com.iflytek.skillhub.domain.social.SkillStarRepository;
import com.iflytek.skillhub.domain.social.SkillSubscription;
import com.iflytek.skillhub.domain.social.SkillSubscriptionRepository;
import com.iflytek.skillhub.domain.user.ProfileChangeRequestRepository;
import com.iflytek.skillhub.notification.domain.NotificationPreferenceRepository;
import com.iflytek.skillhub.notification.domain.NotificationRepository;
import com.iflytek.skillhub.projection.SkillEngagementProjectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * 删除用户时清理其个人从属数据：本地凭据、身份绑定、API 令牌、角色绑定、
 * 收藏、评分、订阅（并重算受影响技能的冗余计数）、站内信、通知偏好与资料变更请求。
 *
 * <p>本类只做机械清理，不做任何业务判断；删除的可行性检查由
 * {@link AdminUserDeletionService} 在调用前完成。必须在删除事务内调用。</p>
 */
@Service
public class UserOwnedDataCleaner {

    private final LocalCredentialRepository localCredentialRepository;
    private final IdentityBindingRepository identityBindingRepository;
    private final ApiTokenRepository apiTokenRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final SkillStarRepository skillStarRepository;
    private final SkillRatingRepository skillRatingRepository;
    private final SkillSubscriptionRepository skillSubscriptionRepository;
    private final SkillEngagementProjectionService engagementProjectionService;
    private final UserNotificationRepository userNotificationRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;

    public UserOwnedDataCleaner(LocalCredentialRepository localCredentialRepository,
                                IdentityBindingRepository identityBindingRepository,
                                ApiTokenRepository apiTokenRepository,
                                UserRoleBindingRepository userRoleBindingRepository,
                                SkillStarRepository skillStarRepository,
                                SkillRatingRepository skillRatingRepository,
                                SkillSubscriptionRepository skillSubscriptionRepository,
                                SkillEngagementProjectionService engagementProjectionService,
                                UserNotificationRepository userNotificationRepository,
                                NotificationRepository notificationRepository,
                                NotificationPreferenceRepository notificationPreferenceRepository,
                                ProfileChangeRequestRepository profileChangeRequestRepository) {
        this.localCredentialRepository = localCredentialRepository;
        this.identityBindingRepository = identityBindingRepository;
        this.apiTokenRepository = apiTokenRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.skillStarRepository = skillStarRepository;
        this.skillRatingRepository = skillRatingRepository;
        this.skillSubscriptionRepository = skillSubscriptionRepository;
        this.engagementProjectionService = engagementProjectionService;
        this.userNotificationRepository = userNotificationRepository;
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.profileChangeRequestRepository = profileChangeRequestRepository;
    }

    /**
     * 清理某用户的全部个人从属数据，并重算受影响技能的收藏/评分/订阅计数。
     * 返回前保证所有从属表不再持有该用户的任何记录。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cleanup(String userId) {
        Set<Long> affectedSkillIds = collectAffectedSkillIds(userId);

        localCredentialRepository.deleteByUserId(userId);
        identityBindingRepository.deleteByUserId(userId);
        apiTokenRepository.deleteByUserId(userId);
        userRoleBindingRepository.deleteByUserId(userId);
        skillStarRepository.deleteByUserId(userId);
        skillRatingRepository.deleteByUserId(userId);
        skillSubscriptionRepository.deleteByUserId(userId);

        affectedSkillIds.forEach(skillId -> {
            engagementProjectionService.refreshStarCount(skillId);
            engagementProjectionService.refreshRatingStats(skillId);
            engagementProjectionService.refreshSubscriptionCount(skillId);
        });

        userNotificationRepository.deleteByUserId(userId);
        notificationRepository.deleteByRecipientId(userId);
        notificationPreferenceRepository.deleteByUserId(userId);
        profileChangeRequestRepository.deleteByUserId(userId);
    }

    /** 汇总该用户收藏/评分/订阅涉及的全部技能 id，删除后需要逐一重算计数。 */
    private Set<Long> collectAffectedSkillIds(String userId) {
        Set<Long> skillIds = new HashSet<>();
        skillStarRepository.findAllByUserId(userId).stream()
                .map(SkillStar::getSkillId)
                .forEach(skillIds::add);
        skillRatingRepository.findAllByUserId(userId).stream()
                .map(SkillRating::getSkillId)
                .forEach(skillIds::add);
        skillSubscriptionRepository.findAllByUserId(userId).stream()
                .map(SkillSubscription::getSkillId)
                .forEach(skillIds::add);
        return skillIds;
    }
}
