package com.sniptext.domain.ai.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.ai.repository.AiToneDailyUsageRepository;
import com.sniptext.domain.ai.service.dto.result.AiToneUsageResult;
import com.sniptext.domain.plan.service.PlanQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiToneUsageQueryService {

	private final AiToneDailyUsageRepository aiToneDailyUsageRepository;
	private final PlanQueryService planQueryService;
	private final Clock clock;

	/** 오늘(KST) 사용량. 한도는 현재 플랜 조회에서 얻는다. 없는 회원·탈퇴 회원이면 USER404_1. */
	public AiToneUsageResult getTodayUsage(Long userId) {
		int limit = planQueryService.getCurrentPlan(userId).aiToneDailyLimit();
		LocalDate today = LocalDate.now(clock);
		int usedCount = aiToneDailyUsageRepository.findUsedCount(userId, today).orElse(0);
		return new AiToneUsageResult(limit > 0, usedCount, limit, Math.max(limit - usedCount, 0),
			today.plusDays(1).atStartOfDay());
	}

}
