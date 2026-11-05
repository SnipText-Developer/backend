package com.sniptext.domain.auth.service.dto.command;

/** 가입 완료 입력. 약관 동의 여부는 요청 단계에서 true만 통과한다. */
public record AuthSignUpCommand(String signupToken) {

	/** 토큰은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "AuthSignUpCommand[]";
	}

}
