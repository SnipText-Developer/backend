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
 * 선점은 "행 확보 → 조건부 증가" 두 단계로 한다. INSERT ... ON DUPLICATE KEY UPDATE 한 번으로 증가까지 하고
 * 그 행 수로 판정하면, 드라이버가 돌려주는 행 수의 의미가 설정에 따라 달라 성공·실패 판정이 흔들린다.
 * 조건부 UPDATE는 바뀐 행 수가 1(성공)/0(실패)로 분명하다.
 * 대량 갱신 쿼리는 감사 기능을 거치지 않으므로 updated_at을 직접 넣는다.
 */
public interface AiToneDailyUsageRepository extends JpaRepository<AiToneDailyUsage, Long> {

	/**
	 * 그날 행이 없으면 0회로 만든다. 이미 있으면 값을 바꾸지 않는다. 돌려주는 행 수는 판정에 쓰지 않는다.
	 * INSERT IGNORE를 쓰지 않는다. 중복 행에 공유 잠금을 걸어, 동시 요청 둘이 공유 잠금을 쥔 채
	 * 이어지는 UPDATE에서 서로의 배타 잠금을 기다리는 교착이 생긴다 (동시 선점 테스트에서 재현).
	 * ON DUPLICATE KEY UPDATE는 중복 행에 배타 잠금을 걸어 두 번째 요청이 첫 번째 커밋까지 기다린다.
	 */
	@Modifying
	@Query(value = """
		INSERT INTO ai_tone_daily_usages (user_id, usage_date, used_count, updated_at)
		VALUES (:userId, :usageDate, 0, :now)
		ON DUPLICATE KEY UPDATE used_count = used_count
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

}
