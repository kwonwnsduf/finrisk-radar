package com.finrisk.radar.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

	boolean existsByEmail(String email);

	Optional<User> findByEmail(String email);

	Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

	long countByPlan(com.finrisk.radar.subscription.PlanType plan);

	long countByCreatedAtAfter(java.time.LocalDateTime after);

	@Query("select user.id from User user where user.role = :role")
	List<Long> findIdsByRole(@Param("role") Role role);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user where user.id = :id")
	Optional<User> findByIdForUpdate(@Param("id") Long id);
}
