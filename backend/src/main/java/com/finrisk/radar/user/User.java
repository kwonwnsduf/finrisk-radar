package com.finrisk.radar.user;

import com.finrisk.radar.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.finrisk.radar.subscription.PlanType;

@Entity
@Table(name = "app_users")
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(nullable = false, length = 255)
	private String password;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;

	@Column(name = "provider_id", length = 255)
	private String providerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlanType plan;

	protected User() {
	}

	private User(
			String email,
			String password,
			String name,
			Role role,
			AuthProvider provider,
			String providerId
	) {
		this.email = email;
		this.password = password;
		this.name = name;
		this.role = role;
		this.plan = PlanType.FREE;
		this.provider = provider;
		this.providerId = providerId;
	}

	public static User create(String email, String password, String name) {
		return new User(email, password, name, Role.ROLE_USER, AuthProvider.LOCAL, null);
	}

	public static User createGoogle(String email, String password, String name, String providerId) {
		return new User(email, password, name, Role.ROLE_USER, AuthProvider.GOOGLE, providerId);
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPassword() {
		return password;
	}

	public String getName() {
		return name;
	}

	public Role getRole() {
		return role;
	}

	public AuthProvider getProvider() {
		return provider;
	}

	public String getProviderId() {
		return providerId;
	}

	public PlanType getPlan() {
		return plan;
	}
}
