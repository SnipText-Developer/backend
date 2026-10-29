package com.sniptext.domain.auth.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * 재발급 토큰 (14일). 원문은 저장하지 않고 SHA-256 해시(16진수 64자)만 저장한다.
 * 재발급할 때마다 새 토큰으로 바꾸고 이전 것은 폐기하며, 같은 로그인에서 이어진 토큰은 family_id가 같다.
 * 이미 폐기된 토큰이 다시 오면 탈취로 보고 그 계보 전체를 폐기한다 (명세 3번).
 */
@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseCreatedTimeEntity {

	public static final int TOKEN_HASH_LENGTH = 64;
	public static final int FAMILY_ID_LENGTH = 36;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "client_type", nullable = false, length = 20)
	private ClientType clientType;

	// 컬럼이 CHAR라 VARCHAR로 매핑하면 스키마 검증(validate)에서 기동이 멈춘다
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, length = TOKEN_HASH_LENGTH)
	private String tokenHash;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "family_id", nullable = false, length = FAMILY_ID_LENGTH)
	private String familyId;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	/** 로그아웃·교체·재사용 감지로 폐기된 시각. null이면 아직 쓸 수 있다 (만료는 따로 본다) */
	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Builder(access = AccessLevel.PRIVATE)
	private RefreshToken(Long userId, ClientType clientType, String tokenHash, String familyId,
		LocalDateTime expiresAt) {
		this.userId = userId;
		this.clientType = clientType;
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.expiresAt = expiresAt;
	}

	/** 여기서 실패하면 토큰 발급 코드의 버그라 IllegalArgumentException(500)이다. 메시지에 해시를 넣지 않는다. */
	public static RefreshToken create(Long userId, ClientType clientType, String tokenHash, String familyId,
		LocalDateTime expiresAt) {
		require(userId != null, "회원 ID가 없다");
		require(clientType != null, "발급처가 없다");
		require(tokenHash != null && tokenHash.length() == TOKEN_HASH_LENGTH, "토큰 해시가 64자가 아니다");
		require(familyId != null && familyId.length() == FAMILY_ID_LENGTH, "계보 ID가 36자가 아니다");
		require(expiresAt != null, "만료 시각이 없다");
		return RefreshToken.builder()
			.userId(userId)
			.clientType(clientType)
			.tokenHash(tokenHash)
			.familyId(familyId)
			.expiresAt(expiresAt)
			.build();
	}

	/** 이미 폐기됐으면 처음 폐기한 시각을 그대로 둔다. */
	public void revoke(LocalDateTime now) {
		require(now != null, "폐기 시각이 없다");
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	/** 만료 시각과 같으면 만료다. */
	public boolean isExpired(LocalDateTime now) {
		return !now.isBefore(expiresAt);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException("재발급 토큰 규칙 위반: " + message);
		}
	}

}
