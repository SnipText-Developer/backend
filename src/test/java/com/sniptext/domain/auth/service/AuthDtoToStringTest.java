package com.sniptext.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sniptext.domain.auth.service.dto.command.AuthLoginCommand;
import com.sniptext.domain.auth.service.dto.command.AuthSignUpCommand;
import com.sniptext.domain.auth.service.dto.result.AuthLoginResult;
import com.sniptext.domain.auth.service.dto.result.AuthSignUpResult;

class AuthDtoToStringTest {

	@Test
	@DisplayName("입력 객체의 toString에 인가 코드와 코드 검증값이 나오지 않는다")
	void loginCommand() {
		String text = new AuthLoginCommand("secret-code", "secret-verifier", "https://sniptext.io/cb").toString();

		assertThat(text).doesNotContain("secret-code", "secret-verifier");
	}

	@Test
	@DisplayName("가입 입력·결과 객체의 toString에 토큰이 나오지 않는다")
	void signUp() {
		assertThat(new AuthSignUpCommand("secret-signup").toString()).doesNotContain("secret-signup");
		assertThat(new AuthSignUpResult("secret-access", "secret-refresh", 1L, "스닙이").toString())
			.doesNotContain("secret-access", "secret-refresh");
	}

	@Test
	@DisplayName("결과 객체의 toString에 토큰과 이메일이 나오지 않는다")
	void loginResult() {
		assertThat(AuthLoginResult.loggedIn("secret-access", "secret-refresh", 1L, "스닙이").toString())
			.doesNotContain("secret-access", "secret-refresh");
		assertThat(AuthLoginResult.signUpRequired("secret-signup", "user@gmail.com", "홍길동", null).toString())
			.doesNotContain("secret-signup", "user@gmail.com");
	}

}
