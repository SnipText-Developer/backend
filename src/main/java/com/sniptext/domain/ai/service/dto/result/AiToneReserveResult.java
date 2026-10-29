package com.sniptext.domain.ai.service.dto.result;

import java.time.LocalDate;

/**
 * 선점한 1회. 반환할 때 이 값을 그대로 넘긴다.
 *
 * @param usageDate      선점한 KST 날짜. 반환은 이 날짜 행에서 한다 (자정을 넘겨도 어제 행에서 뺀다)
 * @param usedCountAfter 선점 후 그날 사용 횟수
 */
public record AiToneReserveResult(Long userId, LocalDate usageDate, int usedCountAfter) {
}
