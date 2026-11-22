package com.sniptext.domain.auth.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sniptext.domain.auth.service.dto.result.AuthSignUpResult;

/** 가입 완료 결과 (명세 2번). 재발급 토큰은 본문에 넣지 않고 쿠키로 내보낸다. */
public record AuthSignUpResponse(
	@JsonProperty("isCompletedSignUp") boolean isCompletedSignUp,
	String accessToken,
	Long userId,
	String nickname
) {

	public static AuthSignUpResponse from(AuthSignUpResult result) {
		return new AuthSignUpResponse(true, result.accessToken(), result.userId(), result.nickname());
	}

}
