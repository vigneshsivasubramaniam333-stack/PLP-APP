package com.plp.iam.repository;

import com.plp.iam.model.entity.User;
import com.plp.iam.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    @Query(value = "SELECT role FROM plp_iam.users WHERE id = :id", nativeQuery = true)
    String findRoleRawById(@Param("id") UUID id);

    boolean existsByEmail(String email);

    List<User> findByRole(UserRole role);

    List<User> findByLinkedEntityId(UUID linkedEntityId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM User u WHERE lower(trim(u.email)) <> lower(trim(:preservedEmail))")
    void deleteAllExceptEmail(@Param("preservedEmail") String preservedEmail);
}
