package com.iflytek.skillhub.auth.local;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository for username-password credentials linked to platform user accounts.
 */
@Repository
public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {

    Optional<LocalCredential> findByUsernameIgnoreCase(String username);

    Optional<LocalCredential> findByUserId(String userId);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUserId(String userId);

    /** 删除该用户的本地登录凭据，供删除用户流程使用。 */
    long deleteByUserId(String userId);
}
