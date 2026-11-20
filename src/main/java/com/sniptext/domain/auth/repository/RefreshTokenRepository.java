package com.sniptext.domain.auth.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sniptext.domain.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * 아직 폐기되지 않은 토큰만 폐기한다. 바뀐 행 수가 1이면 이 요청이 교체를 맡고, 0이면 이미 쓴 토큰이다.
	 * 같은 토큰으로 동시에 와도 UPDATE는 최신 커밋 값을 보므로 한 요청만 1을 받는다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
	int revokeIfActive(@Param("id") Long id, @Param("now") LocalDateTime now);

	/**
	 * 계보 전체를 폐기한다 (이미 쓴 토큰의 재사용 감지). 이미 폐기된 토큰은 처음 폐기 시각을 그대로 둔다.
	 * refresh_tokens에는 updated_at이 없어 직접 넣을 시각 컬럼이 따로 없다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
	int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

	/** 회원의 재발급 토큰을 웹·확장 구분 없이 모두 폐기한다 (로그아웃). */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
	int revokeAllByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

}
