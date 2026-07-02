package com.finrisk.radar.usage;

import com.finrisk.radar.auth.jwt.CustomUserPrincipal;
import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class UsageLimitAspect {
	private final UserRepository userRepository;
	private final UsageLimitService usageLimitService;

	public UsageLimitAspect(UserRepository userRepository, UsageLimitService usageLimitService) {
		this.userRepository = userRepository;
		this.usageLimitService = usageLimitService;
	}

	@Around("@annotation(usageLimit)")
	public Object enforce(ProceedingJoinPoint joinPoint, UsageLimit usageLimit) throws Throwable {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED);
		}
		User user = userRepository.findById(principal.userId())
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (UsagePolicy.isUnlimited(user.getPlan())) return joinPoint.proceed();

		UsageLimitService.UsageReservation reservation = usageLimitService.reserve(
				principal.userId(), usageLimit.value());
		try {
			return joinPoint.proceed();
		} catch (Throwable original) {
			try {
				usageLimitService.release(reservation);
			} catch (BusinessException releaseFailure) {
				original.addSuppressed(releaseFailure);
			}
			throw original;
		}
	}
}
