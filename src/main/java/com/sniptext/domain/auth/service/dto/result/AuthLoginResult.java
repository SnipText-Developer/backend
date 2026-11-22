package com.sniptext.domain.auth.service.dto.result;

/**
 * 구글 로그인 결과. 가입된 회원이면 토큰과 회원 정보가, 처음 온 사용자면 가입용 토큰과 구글 계정 정보가 채워진다.
 * refreshToken은 응답 본문이 아니라 쿠키로 내보낸다.
 */
public record AuthLoginResult(
	boolean completedSignUp,
	String accessToken,
	String refreshToken,
	Long userId,
	String nickname,
	String signupToken,
	String email,
	String name,
	String profileImageUrl
) {

	public static AuthLoginResult loggedIn(String accessToken, String refreshToken, Long userId, String nickname) {
		return new AuthLoginResult(true, accessToken, refreshToken, userId, nickname, null, null, null, null);
	}

	public static AuthLoginResult signUpRequired(String signupToken, String email, String name, String profileImageUrl) {
		return new AuthLoginResult(false, null, null, null, null, signupToken, email, name, profileImageUrl);
	}

	/** 토큰과 이메일은 로그에 남기지 않는다. */
	@Override
	public String toString() {
		return "AuthLoginResult[completedSignUp=%s, userId=%s]".formatted(completedSignUp, userId);
	}

}
