package com.iflytek.skillhub.auth.merge;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for pending account-merge requests between two platform identities.
 */
@Repository
public interface AccountMergeRequestRepository extends JpaRepository<AccountMergeRequest, Long> {

    Optional<AccountMergeRequest> findByIdAndPrimaryUserId(Long id, String primaryUserId);

    boolean existsBySecondaryUserIdAndStatus(String secondaryUserId, String status);

    /** 该用户是否作为主账号出现在任何合并请求中，供删除用户前的关联数据检查。 */
    boolean existsByPrimaryUserId(String primaryUserId);

    /** 该用户是否作为待合并账号出现在任何合并请求中，供删除用户前的关联数据检查。 */
    boolean existsBySecondaryUserId(String secondaryUserId);
}
