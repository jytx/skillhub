package com.iflytek.skillhub.notification.domain;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository {
    NotificationPreference save(NotificationPreference preference);
    List<NotificationPreference> findByUserId(String userId);
    Optional<NotificationPreference> findByUserIdAndCategoryAndChannel(
            String userId, NotificationCategory category, NotificationChannel channel);
    /** 删除该用户的全部通知偏好，供删除用户流程使用。 */
    long deleteByUserId(String userId);
}
