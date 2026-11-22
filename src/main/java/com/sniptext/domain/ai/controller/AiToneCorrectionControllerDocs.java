package com.sniptext.domain.ai.controller;

import org.springframework.http.ResponseEntity;

import com.sniptext.domain.ai.controller.dto.AiToneCorrectRequest;
import com.sniptext.domain.ai.controller.dto.AiToneCorrectResponse;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * AI 톤 교정 API의 스웨거 설명. 매핑·검증은 {@link AiToneCorrectionController}에 있다.
 * 자세한 규칙은 docs/s1-api-spec.md 22번이 기준이다.
 */
@Tag(name = "AI 톤 교정", description = "문장을 고른 문체로 다듬기 (회원마다 하루 사용 횟수 제한)")
public interface AiToneCorrectionControllerDocs {

	@Operation(summary = "AI 톤 교정 실행",
		description = "요청할 때 1회를 먼저 차감하고, 외부 AI 오류·20초 시간 초과·부적절한 표현 차단이면 되돌립니다. "
			+ "원문의 날짜·금액 등이 결과에서 빠지거나 바뀌면 keyInfoWarning을 내려주고, 문제가 없으면 null입니다.")
	ResponseEntity<ApiResponse<AiToneCorrectResponse>> correct(@LoginUser Long userId, AiToneCorrectRequest request);

}
