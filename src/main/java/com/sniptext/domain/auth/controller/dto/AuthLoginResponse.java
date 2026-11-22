package com.sniptext.domain.auth.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sniptext.domain.auth.service.dto.result.AuthLoginResult;

/** 구글 로그인 결과 (명세 1번). 재발급 토큰은 본문에 넣지 않고 쿠키로 내보낸다. */
public record AuthLoginResponse(
	@JsonProperty("isCompletedSignUp") boolean isCompletedSignUp,
	String accessToken,
	Long userId,
	String nickname,
	String signupToken,
	String email,
	String name,
	String profileImageUrl
) {

	public static AuthLoginResponse from(AuthLoginResult result) {
		return new AuthLoginResponse(
			result.completedSignUp(),
			result.accessToken(),
			result.userId(),
			result.nickname(),
			result.signupToken(),
			result.email(),
			result.name(),
			result.profileImageUrl()
		);
	}

}
