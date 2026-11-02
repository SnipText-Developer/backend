package com.sniptext.domain.ai.service.dto.result;

import java.time.LocalDateTime;

/**
 * 오늘 AI 톤 교정 사용량.
 *
 * @param usedCount 오늘 사용 횟수
 * @param limit     하루 한도
 * @param remaining 오늘 남은 횟수 (0 미만으로 내려가지 않는다)
 * @param resetAt   다음 리셋 시각 (다음 날 00:00 KST)
 */
public record AiToneUsageResult(int usedCount, int limit, int remaining, LocalDateTime resetAt) {
}
