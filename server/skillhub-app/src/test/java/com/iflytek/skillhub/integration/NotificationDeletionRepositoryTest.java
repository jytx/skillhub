package com.iflytek.skillhub.integration;

import com.iflytek.skillhub.infra.jpa.NotificationJpaRepository;
import com.iflytek.skillhub.notification.domain.Notification;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.domain.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 删除用户清理链路中通知仓储的 JPQL 修改查询验证。
 *
 * <p>背景：deleteByRecipientId 曾声明 long 返回类型，@Modifying 查询只允许
 * void/int，问题在纯 Mockito 单测中不可见、首次真实调用才抛
 * IllegalArgumentException 导致删除用户接口 500。此 slice 测试用真实
 * Spring Data 执行链路防回归。</p>
 */
@DataJpaTest
@ActiveProfiles("test")
class NotificationDeletionRepositoryTest {

    @Autowired
    private NotificationJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deleteByRecipientId_removesOnlyThatRecipientsNotifications() {
        entityManager.persist(notification("user-1", "evt-a"));
        entityManager.persist(notification("user-1", "evt-b"));
        entityManager.persist(notification("user-2", "evt-c"));
        entityManager.flush();

        int deleted = repository.deleteByRecipientId("user-1");
        entityManager.clear();

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.countByRecipientIdAndStatus("user-2", NotificationStatus.UNREAD))
                .isEqualTo(1);
    }

    private Notification notification(String recipientId, String eventType) {
        return new Notification(
                recipientId, NotificationCategory.PUBLISH, eventType, "title", null, null, null,
                Instant.parse("2026-09-16T09:00:00Z"));
    }
}
