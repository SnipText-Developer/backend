package com.sniptext.domain.ai.service.dto.result;

import java.time.LocalDateTime;

/**
 * 오늘 AI 톤 교정 사용량.
 *
 * @param available 현재 플랜에서 쓸 수 있는지 (하루 한도가 0보다 큰지)
 * @param usedCount 오늘 사용 횟수
 * @param limit     하루 한도
 * @param remaining 오늘 남은 횟수 (0 미만으로 내려가지 않는다)
 * @param resetAt   다음 리셋 시각 (다음 날 00:00 KST)
 */
public record AiToneUsageResult(boolean available, int usedCount, int limit, int remaining, LocalDateTime resetAt) {
}
