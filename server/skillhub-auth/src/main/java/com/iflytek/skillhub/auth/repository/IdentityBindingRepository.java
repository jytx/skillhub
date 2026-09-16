package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.IdentityBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * JPA repository for links between platform users and external identity-provider subjects.
 */
@Repository
public interface IdentityBindingRepository extends JpaRepository<IdentityBinding, Long> {
    Optional<IdentityBinding> findByProviderCodeAndSubject(String providerCode, String subject);
    java.util.List<IdentityBinding> findByUserId(String userId);

    /** 删除该用户的全部外部身份绑定，供删除用户流程使用。 */
    long deleteByUserId(String userId);
}
