package com.iflytek.skillhub.domain.social;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkillSubscriptionRepository {
    SkillSubscription save(SkillSubscription subscription);
    Optional<SkillSubscription> findBySkillIdAndUserId(Long skillId, String userId);
    void delete(SkillSubscription subscription);
    void deleteBySkillId(Long skillId);
    Page<SkillSubscription> findByUserId(String userId, Pageable pageable);
    List<SkillSubscription> findAllBySkillId(Long skillId);
    /** 该用户的全部订阅记录（不分页），供删除用户时重算技能计数使用。 */
    List<SkillSubscription> findAllByUserId(String userId);
    /** 删除该用户的全部订阅，返回删除条数。 */
    long deleteByUserId(String userId);
    long countBySkillId(Long skillId);
}
