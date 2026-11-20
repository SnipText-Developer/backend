package com.sniptext.domain.auth.service.dto.command;

/** 재발급 입력. 쿠키가 없으면 refreshToken은 null이다. */
public record AuthReissueCommand(String refreshToken) {

	/** 토큰은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "AuthReissueCommand[]";
	}

}
