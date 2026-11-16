package com.sniptext.domain.auth.service.dto.command;

/** 구글 로그인 입력. 값은 요청 단계에서 빈 값 검증을 마쳤다. */
public record AuthLoginCommand(String code, String codeVerifier, String redirectUri) {

	/** 인가 코드와 코드 검증값은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "AuthLoginCommand[redirectUri=%s]".formatted(redirectUri);
	}

}
