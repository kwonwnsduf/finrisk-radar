package com.finrisk.radar.user;

import com.finrisk.radar.global.error.BusinessException;
import com.finrisk.radar.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository);
	}

	@Test
	void returnsCurrentUserInformation() {
		User user = User.create("user@example.com", "encoded-password", "User");
		ReflectionTestUtils.setField(user, "id", 42L);
		when(userRepository.findById(42L)).thenReturn(Optional.of(user));

		MeResponse response = userService.getMe(42L);

		assertThat(response.id()).isEqualTo(42L);
		assertThat(response.email()).isEqualTo("user@example.com");
		assertThat(response.name()).isEqualTo("User");
		assertThat(response.role()).isEqualTo("ROLE_USER");
		assertThat(response.plan()).isEqualTo("FREE");
	}

	@Test
	void returnsUnauthorizedWhenTokenUserNoLongerExists() {
		when(userRepository.findById(42L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.getMe(42L))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).getErrorCode())
				.isEqualTo(ErrorCode.UNAUTHORIZED);
	}
}
