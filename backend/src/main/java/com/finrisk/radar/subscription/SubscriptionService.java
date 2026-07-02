package com.finrisk.radar.subscription;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import com.finrisk.radar.user.User;
import com.finrisk.radar.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
	private final UserRepository userRepository;

	public SubscriptionService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public SubscriptionResponse getCurrent(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (user.getPlan() == null) throw new BusinessException(ErrorCode.USER_PLAN_NOT_FOUND);
		return new SubscriptionResponse(user.getPlan().name());
	}
}
