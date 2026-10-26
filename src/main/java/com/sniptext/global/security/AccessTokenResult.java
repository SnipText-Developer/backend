package com.sniptext.global.security;

/** 접근 토큰 해석 결과. 만료는 재발급 대상(COMMON401_1), 무효는 다시 로그인 대상(COMMON401_2)이다. */
public sealed interface AccessTokenResult {

	record Authenticated(long userId) implements AccessTokenResult {
	}

	record Expired() implements AccessTokenResult {
	}

	record Invalid() implements AccessTokenResult {
	}

}
