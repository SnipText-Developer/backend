package com.sniptext.domain.ai.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sniptext.domain.ai.entity.AiToneDailyUsage;

/**
 * 선점은 "행 확보 → 조건부 증가" 두 단계로 한다. INSERT ... ON DUPLICATE KEY UPDATE 한 번으로 하면
 * 드라이버가 돌려주는 행 수의 의미가 설정에 따라 달라 성공·실패 판정이 흔들린다.
 * 조건부 UPDATE는 바뀐 행 수가 1(성공)/0(실패)로 분명하다.
 * 대량 갱신 쿼리는 감사 기능을 거치지 않으므로 updated_at을 직접 넣는다.
 */
public interface AiToneDailyUsageRepository extends JpaRepository<AiToneDailyUsage, Long> {

	/**
	 * 그날 행이 없으면 0회로 만든다. 이미 있으면 아무것도 하지 않는다.
	 * INSERT IGNORE는 외래키 위반도 경고로 넘기므로, 회원 존재 확인은 부르는 쪽(플랜 조회)에서 먼저 한다.
	 */
	@Modifying
	@Query(value = """
		INSERT IGNORE INTO ai_tone_daily_usages (user_id, usage_date, used_count, updated_at)
		VALUES (:userId, :usageDate, 0, :now)
		""", nativeQuery = true)
	int insertIfAbsent(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
		@Param("now") LocalDateTime now);

	/** used_count가 한도 미만일 때만 1 올린다. 바뀐 행 수(1 성공 / 0 한도 도달)를 돌려준다. */
	@Modifying
	@Query(value = """
		UPDATE ai_tone_daily_usages
		SET used_count = used_count + 1, updated_at = :now
		WHERE user_id = :userId AND usage_date = :usageDate AND used_count < :limit
		""", nativeQuery = true)
	int incrementIfBelow(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
		@Param("limit") int limit, @Param("now") LocalDateTime now);

	/** used_count를 1 내린다. 0이면 그대로 둔다. */
	@Modifying
	@Query(value = """
		UPDATE ai_tone_daily_usages
		SET used_count = used_count - 1, updated_at = :now
		WHERE user_id = :userId AND usage_date = :usageDate AND used_count > 0
		""", nativeQuery = true)
	int decrementIfPositive(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate,
		@Param("now") LocalDateTime now);

	@Query("select u.usedCount from AiToneDailyUsage u where u.userId = :userId and u.usageDate = :usageDate")
	Optional<Integer> findUsedCount(@Param("userId") Long userId, @Param("usageDate") LocalDate usageDate);

	Optional<AiToneDailyUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

}
