package com.sniptext.domain.ai.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.ai.repository.AiToneDailyUsageRepository;
import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;
import com.sniptext.global.policy.UsageLimits;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiToneUsageQueryService {

	private final AiToneDailyUsageRepository aiToneDailyUsageRepository;
	private final Clock clock;

	/** 오늘(KST) 사용량. 한도는 모든 사용자에게 같다 (UsageLimits). 오늘 행이 없으면 사용 0이다. */
	public AiToneUsageResult getTodayUsage(Long userId) {
		int limit = UsageLimits.AI_TONE_DAILY_LIMIT;
		LocalDate today = LocalDate.now(clock);
		int usedCount = aiToneDailyUsageRepository.findUsedCount(userId, today).orElse(0);
		return new AiToneUsageResult(usedCount, limit, Math.max(limit - usedCount, 0),
			today.plusDays(1).atStartOfDay());
	}

}
