package com.sniptext.domain.ai.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 톤 교정 하루 사용량. 회원·KST 날짜별 1행이라 자정 리셋 배치가 필요 없다. 원문·결과는 저장하지 않는다.
 * 행 생성과 횟수 변경은 레포지토리의 대량 갱신 쿼리로만 한다 (조건부 증가로 동시 요청에도 한도를 넘지 않게).
 * created_at이 없어 BaseTimeEntity를 상속하지 않고, updated_at은 쿼리에서 직접 넣는다.
 */
@Getter
@Entity
@Table(name = "ai_tone_daily_usages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiToneDailyUsage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** KST 기준 날짜 */
	@Column(name = "usage_date", nullable = false)
	private LocalDate usageDate;

	@Column(name = "used_count", nullable = false)
	private int usedCount;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

}
