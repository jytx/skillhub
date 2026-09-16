package com.iflytek.skillhub.domain.social;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain repository contract for skill star relationships and starred-skill pagination.
 */
public interface SkillStarRepository {
    SkillStar save(SkillStar star);
    Optional<SkillStar> findBySkillIdAndUserId(Long skillId, String userId);
    void delete(SkillStar star);
    void deleteBySkillId(Long skillId);
    Page<SkillStar> findByUserId(String userId, Pageable pageable);
    /** 该用户的全部收藏记录（不分页），供删除用户时重算技能计数使用。 */
    List<SkillStar> findAllByUserId(String userId);
    /** 删除该用户的全部收藏，返回删除条数。 */
    long deleteByUserId(String userId);
    long countBySkillId(Long skillId);
}
