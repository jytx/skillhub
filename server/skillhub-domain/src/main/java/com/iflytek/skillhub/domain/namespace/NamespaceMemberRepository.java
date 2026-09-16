package com.iflytek.skillhub.domain.namespace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for namespace membership lookups and member administration.
 */
public interface NamespaceMemberRepository {
    Optional<NamespaceMember> findByNamespaceIdAndUserId(Long namespaceId, String userId);
    List<NamespaceMember> findByUserId(String userId);
    Page<NamespaceMember> findByNamespaceId(Long namespaceId, Pageable pageable);
    List<NamespaceMember> findByNamespaceIdAndRoleIn(Long namespaceId, Collection<NamespaceRole> roles);
    List<NamespaceMember> findByNamespaceIdAndUserIdIn(Long namespaceId, Collection<String> userIds);
    NamespaceMember save(NamespaceMember member);
    void deleteByNamespaceId(Long namespaceId);
    void deleteByNamespaceIdAndUserId(Long namespaceId, String userId);
    /** 删除某用户在所有命名空间下的成员关系，供删除用户流程使用。 */
    long deleteByUserId(String userId);
}
