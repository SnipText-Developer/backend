package com.sniptext.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static com.sniptext.support.TestTokens.sign;
import static com.sniptext.support.TestTokens.tamperSignature;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sniptext.support.MutableClock;

/** 가입용 토큰(typ=SIGNUP) 발급·해석. 접근 토큰 규칙은 JwtTokenProviderTest에서 본다. */
class SignupTokenTest {

	private static final String SECRET = "unit-test-jwt-secret-0123456789abcdef";
	private static final String OTHER_SECRET = "another-jwt-secret-0123456789abcdefgh";
	private static final SignupTokenClaims GOOGLE_ACCOUNT = new SignupTokenClaims(
		"109876543210987654321", "signup@gmail.com", "홍길동", "https://lh3.googleusercontent.com/a/sample=s96-c");

	private MutableClock clock;
	private JwtTokenProvider provider;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(LocalDateTime.of(2026, 10, 29, 21, 0));
		provider = new JwtTokenProvider(SECRET, clock);
	}

	@Test
	@DisplayName("발급한 가입용 토큰을 해석하면 구글 계정 정보가 그대로 나온다")
	void issueAndParse() {
		String token = provider.issueSignupToken(GOOGLE_ACCOUNT);

		assertThat(provider.parseSignupToken(token)).contains(GOOGLE_ACCOUNT);
	}

	@Test
	@DisplayName("구글이 이름·프로필 이미지를 주지 않았으면 해석해도 null이다")
	void withoutNameAndPicture() {
		SignupTokenClaims minimal = new SignupTokenClaims("1234", "minimal@gmail.com", null, null);

		assertThat(provider.parseSignupToken(provider.issueSignupToken(minimal))).contains(minimal);
	}

	@Test
	@DisplayName("발급 29분 뒤에는 유효하고 31분 뒤에는 빈 값이다 (가입 시간 초과)")
	void expiresAfter30Minutes() {
		String token = provider.issueSignupToken(GOOGLE_ACCOUNT);

		clock.advance(Duration.ofMinutes(29));
		assertThat(provider.parseSignupToken(token)).isPresent();

		clock.advance(Duration.ofMinutes(2));
		assertThat(provider.parseSignupToken(token)).isEmpty();
	}

	@Test
	@DisplayName("발급 30분이 되는 순간부터 빈 값이다 (만료 시각과 같으면 만료)")
	void expiresAtExactly30Minutes() {
		String token = provider.issueSignupToken(GOOGLE_ACCOUNT);

		clock.advance(Duration.ofMinutes(30).minusSeconds(1));
		assertThat(provider.parseSignupToken(token)).isPresent();

		clock.advance(Duration.ofSeconds(1));
		assertThat(provider.parseSignupToken(token)).isEmpty();
	}

	@Test
	@DisplayName("접근 토큰은 가입용 토큰으로, 가입용 토큰은 접근 토큰으로 쓸 수 없다")
	void typesAreNotInterchangeable() {
		String accessToken = provider.issueAccessToken(7L);
		String signupToken = provider.issueSignupToken(GOOGLE_ACCOUNT);

		assertThat(provider.parseSignupToken(accessToken)).isEmpty();
		assertThat(provider.parseAccessToken(signupToken)).isInstanceOf(AccessTokenResult.Invalid.class);
	}

	@Test
	@DisplayName("서명을 바꾸거나 다른 키로 서명한 가입용 토큰은 빈 값이다")
	void tamperedSignature() {
		String token = provider.issueSignupToken(GOOGLE_ACCOUNT);
		String otherKeyToken = new JwtTokenProvider(OTHER_SECRET, clock).issueSignupToken(GOOGLE_ACCOUNT);

		assertThat(provider.parseSignupToken(tamperSignature(token))).isEmpty();
		assertThat(provider.parseSignupToken(otherKeyToken)).isEmpty();
	}

	@Test
	@DisplayName("이메일이 없는 가입용 토큰은 같은 키로 서명돼도 빈 값이다")
	void withoutEmail() {
		String noEmail = sign(SECRET, "SIGNUP", "1234", clock.instant().plus(Duration.ofMinutes(30)));

		assertThat(provider.parseSignupToken(noEmail)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "a.b.c", "", "   ", "한글!@#.토큰$%^.깨짐&*()"})
	@DisplayName("형식이 깨진 문자열은 예외 없이 빈 값이다")
	void malformed(String token) {
		assertThat(provider.parseSignupToken(token)).isEmpty();
	}

}
