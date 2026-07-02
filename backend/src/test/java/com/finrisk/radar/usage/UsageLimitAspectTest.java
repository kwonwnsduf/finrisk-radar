package com.finrisk.radar.usage;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.subscription.PlanType;
import com.finrisk.radar.user.Role;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageLimitAspectTest {
	@Mock UserRepository userRepository;
	@Mock UsageLimitService usageLimitService;
	@Mock ProceedingJoinPoint joinPoint;
	@Mock UsageLimit usageLimit;
	private UsageLimitAspect aspect;

	@BeforeEach
	void setUp() {
		aspect = new UsageLimitAspect(userRepository, usageLimitService);
		CustomUserPrincipal principal = new CustomUserPrincipal(
				42L, "user@example.com", Role.ROLE_USER, "jti", Instant.now().plusSeconds(60));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
		lenient().when(usageLimit.value()).thenReturn(UsageType.BACKTEST);
	}

	@AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

	@Test
	void freePlanReservesUsageAndReturnsResult() throws Throwable {
		User user = User.create("user@example.com", "password", "User");
		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(usageLimitService.reserve(42L, UsageType.BACKTEST))
				.thenReturn(new UsageLimitService.UsageReservation("usage-key"));
		when(joinPoint.proceed()).thenReturn("ok");

		assertThat(aspect.enforce(joinPoint, usageLimit)).isEqualTo("ok");
		verify(usageLimitService, never()).release(any());
	}

	@Test
	void failedCallReleasesReservedUsage() throws Throwable {
		User user = User.create("user@example.com", "password", "User");
		UsageLimitService.UsageReservation reservation = new UsageLimitService.UsageReservation("usage-key");
		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(usageLimitService.reserve(42L, UsageType.BACKTEST)).thenReturn(reservation);
		when(joinPoint.proceed()).thenThrow(new IllegalStateException("failed"));

		assertThatThrownBy(() -> aspect.enforce(joinPoint, usageLimit))
				.isInstanceOf(IllegalStateException.class);
		verify(usageLimitService).release(reservation);
	}

	@Test
	void premiumPlanBypassesRedis() throws Throwable {
		User user = User.create("user@example.com", "password", "User");
		ReflectionTestUtils.setField(user, "plan", PlanType.PREMIUM);
		when(userRepository.findById(42L)).thenReturn(Optional.of(user));
		when(joinPoint.proceed()).thenReturn("ok");

		assertThat(aspect.enforce(joinPoint, usageLimit)).isEqualTo("ok");
		verifyNoInteractions(usageLimitService);
	}

	@Test
	void missingAuthenticationIsUnauthorized() {
		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> aspect.enforce(joinPoint, usageLimit))
				.isInstanceOf(BusinessException.class);
	}
}
