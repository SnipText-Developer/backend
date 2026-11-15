package com.sniptext.domain.auth.service.dto.command;

/** 로그아웃 입력. 쿠키가 없으면 refreshToken은 null이다. */
public record AuthLogoutCommand(String refreshToken) {

	/** 토큰은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "AuthLogoutCommand[]";
	}

}
