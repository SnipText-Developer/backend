package com.sniptext.domain.auth.controller.dto;

import com.sniptext.domain.auth.service.dto.result.AuthReissueResult;

/** 재발급 결과 (명세 3번). 새 재발급 토큰은 본문에 넣지 않고 쿠키로 내보낸다. */
public record AuthReissueResponse(String accessToken) {

	public static AuthReissueResponse from(AuthReissueResult result) {
		return new AuthReissueResponse(result.accessToken());
	}

}
