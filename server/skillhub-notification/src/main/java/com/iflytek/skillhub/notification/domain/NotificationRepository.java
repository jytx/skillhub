package com.iflytek.skillhub.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findById(Long id);
    Page<Notification> findByRecipientId(String recipientId, Pageable pageable);
    Page<Notification> findByRecipientIdAndCategory(String recipientId, NotificationCategory category, Pageable pageable);
    long countByRecipientIdAndStatus(String recipientId, NotificationStatus status);
    int markAllReadByRecipientId(String recipientId, Instant readAt);
    int deleteByIdAndRecipientIdAndStatus(Long id, String recipientId, NotificationStatus status);
    int deleteByStatusAndCreatedAtBefore(NotificationStatus status, Instant before);
    /** 删除某收件人的全部通知，供删除用户流程使用。@Modifying 查询仅允许 int 返回。 */
    int deleteByRecipientId(String recipientId);
}
