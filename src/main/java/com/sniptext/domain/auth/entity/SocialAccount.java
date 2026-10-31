package com.sniptext.domain.auth.entity;

import com.sniptext.global.entity.BaseCreatedTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원과 소셜 계정의 연결. 로그인할 때 (제공자, 구글 계정 고유값)으로 가입된 회원을 찾는다.
 * 같은 소셜 계정은 한 번만 연결된다 (uk_social_provider_user). 회원은 user 도메인이라 ID로만 들고 있다.
 */
@Getter
@Entity
@Table(name = "social_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseCreatedTimeEntity {

	public static final int PROVIDER_USER_ID_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 20)
	private SocialProvider provider;

	/** 구글 ID 토큰의 sub. 이메일은 바뀔 수 있어 이 값으로 계정을 구분한다 */
	@Column(name = "provider_user_id", nullable = false, length = PROVIDER_USER_ID_MAX_LENGTH)
	private String providerUserId;

	@Builder(access = AccessLevel.PRIVATE)
	private SocialAccount(Long userId, SocialProvider provider, String providerUserId) {
		this.userId = userId;
		this.provider = provider;
		this.providerUserId = providerUserId;
	}

	/** 가입 완료 때 만든다. 여기서 실패하면 가입 처리의 버그라 IllegalArgumentException(500)이다. */
	public static SocialAccount create(Long userId, SocialProvider provider, String providerUserId) {
		require(userId != null, "회원 ID가 없다");
		require(provider != null, "제공자가 없다");
		require(providerUserId != null && !providerUserId.isBlank(), "소셜 계정 고유값이 없다");
		require(providerUserId.length() <= PROVIDER_USER_ID_MAX_LENGTH, "소셜 계정 고유값이 100자를 넘는다");
		return SocialAccount.builder()
			.userId(userId)
			.provider(provider)
			.providerUserId(providerUserId)
			.build();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException("소셜 계정 생성 규칙 위반: " + message);
		}
	}

}
