package com.sniptext.domain.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.ai.controller.dto.AiToneCorrectRequest;
import com.sniptext.domain.ai.controller.dto.AiToneCorrectResponse;
import com.sniptext.domain.ai.service.AiToneCorrectionCommandService;
import com.sniptext.domain.ai.service.dto.result.AiToneCorrectResult;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AI 톤 교정 (로그인 필요, 캐시 금지).
 */
@RestController
@RequestMapping("/api/v1/ai/tone-corrections")
@RequiredArgsConstructor
public class AiToneCorrectionController {

	private final AiToneCorrectionCommandService aiToneCorrectionCommandService;

	@PostMapping
	public ResponseEntity<ApiResponse<AiToneCorrectResponse>> correct(
		@LoginUser Long userId,
		@Valid @RequestBody AiToneCorrectRequest request) {

		AiToneCorrectResult result = aiToneCorrectionCommandService.correct(request.toCommand(userId));

		return ApiResponse.success(AiSuccessCode.TONE_CORRECTED, AiToneCorrectResponse.from(result));
	}

}
