package com.sniptext.domain.user.entity;

import java.time.LocalDateTime;

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
 * 약관 동의 기록. 회원이 어떤 약관의 어느 버전에 언제 동의했는지 남긴다 (약관 본문은 DB에 두지 않는다).
 * 동의 시각을 agreed_at에 직접 받으므로 시각 자동 기록 부모를 상속하지 않는다.
 */
@Getter
@Entity
@Table(name = "user_terms_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermsAgreement {

	public static final int VERSION_MAX_LENGTH = 20;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "terms_type", nullable = false, length = 20)
	private TermsType termsType;

	@Column(name = "version", nullable = false, length = VERSION_MAX_LENGTH)
	private String version;

	@Column(name = "agreed_at", nullable = false)
	private LocalDateTime agreedAt;

	@Builder(access = AccessLevel.PRIVATE)
	private UserTermsAgreement(Long userId, TermsType termsType, String version, LocalDateTime agreedAt) {
		this.userId = userId;
		this.termsType = termsType;
		this.version = version;
		this.agreedAt = agreedAt;
	}

	/** 여기서 실패하면 가입 서비스의 버그다. 사용자 입력이 아니므로 IllegalArgumentException(500)을 던진다. */
	public static UserTermsAgreement create(Long userId, TermsType termsType, String version,
		LocalDateTime agreedAt) {
		require(userId != null, "회원 ID가 없다");
		require(termsType != null, "약관 종류가 없다");
		require(version != null && !version.isBlank(), "약관 버전이 없다");
		require(version.length() <= VERSION_MAX_LENGTH, "약관 버전이 20자를 넘는다");
		require(agreedAt != null, "동의 시각이 없다");
		return UserTermsAgreement.builder()
			.userId(userId)
			.termsType(termsType)
			.version(version)
			.agreedAt(agreedAt)
			.build();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException("약관 동의 생성 규칙 위반: " + message);
		}
	}

}
