package com.iflytek.skillhub.domain.social;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain repository contract for per-user ratings and rating aggregates on one skill.
 */
public interface SkillRatingRepository {
    SkillRating save(SkillRating rating);
    void flush();
    Optional<SkillRating> findById(Long id);
    Optional<SkillRating> findBySkillIdAndUserId(Long skillId, String userId);
    Page<SkillRating> findVisibleReviewsBySkillId(Long skillId, Pageable pageable);
    Page<SkillRating> findReviewsBySkillId(Long skillId, Pageable pageable);
    double averageScoreBySkillId(Long skillId);
    int countBySkillId(Long skillId);
    void deleteBySkillId(Long skillId);
    /** 该用户的全部评分记录（不分页），供删除用户时重算技能计数使用。 */
    List<SkillRating> findAllByUserId(String userId);
    /** 删除该用户的全部评分，返回删除条数。 */
    long deleteByUserId(String userId);
}
