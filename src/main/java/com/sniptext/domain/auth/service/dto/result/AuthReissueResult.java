package com.sniptext.domain.auth.service.dto.result;

/** 재발급 결과. refreshToken은 응답 본문이 아니라 쿠키로 내보낸다. */
public record AuthReissueResult(String accessToken, String refreshToken) {

	/** 토큰은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "AuthReissueResult[]";
	}

}
