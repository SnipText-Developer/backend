package com.sniptext.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.ai.service.AiToneUsageQueryService;
import com.sniptext.domain.template.service.TemplateQueryService;
import com.sniptext.domain.user.service.dto.result.UserUsageResult;

import lombok.RequiredArgsConstructor;

/**
 * 사용량 조회. template·ai 서비스를 조합하므로 UserQueryService와 분리한다.
 * UserQueryService에 넣으면 template·ai가 UserQueryService를 부르게 될 때 생성자 주입이 순환해 앱이 뜨지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserUsageQueryService {

	private final UserQueryService userQueryService;
	private final TemplateQueryService templateQueryService;
	private final AiToneUsageQueryService aiToneUsageQueryService;

	/** 없거나 탈퇴한 회원이면 USER404_1. */
	public UserUsageResult getUsage(Long userId) {
		userQueryService.validateActive(userId);

		return UserUsageResult.of(templateQueryService.countActive(userId),
			aiToneUsageQueryService.getTodayUsage(userId));
	}

}
