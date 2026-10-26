package com.sniptext.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.sniptext.support.TestTokens.sign;
import static com.sniptext.support.TestTokens.tamperSignature;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.sniptext.support.MutableClock;

class JwtTokenProviderTest {

	private static final String SECRET = "unit-test-jwt-secret-0123456789abcdef";
	private static final String OTHER_SECRET = "another-jwt-secret-0123456789abcdefgh";

	private MutableClock clock;
	private JwtTokenProvider provider;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(LocalDateTime.of(2026, 10, 25, 10, 0));
		provider = new JwtTokenProvider(SECRET, clock);
	}

	@Test
	@DisplayName("발급한 접근 토큰을 해석하면 회원 번호가 나온다")
	void issueAndParse() {
		String token = provider.issueAccessToken(7L);

		assertThat(provider.parseAccessToken(token)).isEqualTo(new AccessTokenResult.Authenticated(7L));
	}

	@Test
	@DisplayName("발급 29분 뒤에는 유효하고 31분 뒤에는 만료다")
	void expiresAfter30Minutes() {
		String token = provider.issueAccessToken(7L);

		clock.advance(Duration.ofMinutes(29));
		assertThat(provider.parseAccessToken(token)).isInstanceOf(AccessTokenResult.Authenticated.class);

		clock.advance(Duration.ofMinutes(2));
		assertThat(provider.parseAccessToken(token)).isInstanceOf(AccessTokenResult.Expired.class);
	}

	@Test
	@DisplayName("서명을 바꾸거나 다른 키로 서명한 토큰은 무효다")
	void tamperedSignature() {
		String token = provider.issueAccessToken(7L);
		String otherKeyToken = new JwtTokenProvider(OTHER_SECRET, clock).issueAccessToken(7L);

		assertThat(provider.parseAccessToken(tamperSignature(token))).isInstanceOf(AccessTokenResult.Invalid.class);
		assertThat(provider.parseAccessToken(otherKeyToken)).isInstanceOf(AccessTokenResult.Invalid.class);
	}

	@Test
	@DisplayName("종류(typ)가 ACCESS가 아닌 토큰은 같은 키로 서명돼도 무효다")
	void otherType() {
		String signupToken = sign(SECRET, "SIGNUP", "7", clock.instant().plus(Duration.ofMinutes(30)));

		assertThat(provider.parseAccessToken(signupToken)).isInstanceOf(AccessTokenResult.Invalid.class);
	}

	@Test
	@DisplayName("회원 번호가 숫자가 아니거나 서명이 없는(alg: none) 토큰은 무효다")
	void invalidSubjectAndUnsigned() {
		String badSubject = sign(SECRET, "ACCESS", "abc", clock.instant().plus(Duration.ofMinutes(30)));
		String unsigned = base64Url("{\"alg\":\"none\"}") + "."
			+ base64Url("{\"sub\":\"7\",\"typ\":\"ACCESS\",\"exp\":4102444800}") + ".";

		assertThat(provider.parseAccessToken(badSubject)).isInstanceOf(AccessTokenResult.Invalid.class);
		assertThat(provider.parseAccessToken(unsigned)).isInstanceOf(AccessTokenResult.Invalid.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "a.b.c", "", "   ",
		"qwertyuiopasdfghjklzxcvbnmqwertyuiopasdfghjklzxcvbnmqwertyuiopasdfghjklzxcvbnm1234567890",
		"한글!@#.토큰$%^.깨짐&*()", "eyJ*.e%J.~~~"})
	@DisplayName("형식이 깨진 문자열은 예외 없이 무효로 해석한다")
	void malformed(String token) {
		assertThat(provider.parseAccessToken(token)).isInstanceOf(AccessTokenResult.Invalid.class);
	}

	@Test
	@DisplayName("서명 키가 32바이트 미만이면 생성에 실패하고, 메시지에 키를 넣지 않는다")
	void shortSecret() {
		String shortSecret = "a".repeat(31);

		assertThatThrownBy(() -> new JwtTokenProvider(shortSecret, clock))
			.isInstanceOf(IllegalStateException.class)
			.message().doesNotContain(shortSecret);
		assertThat(new JwtTokenProvider("a".repeat(32), clock).issueAccessToken(1L)).isNotBlank();
	}

	@Test
	@DisplayName("서명 키 설정이 없거나 32바이트 미만이면 컨텍스트가 뜨지 않는다")
	void contextFailsWithoutValidSecret() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
			.withBean(Clock.class, Clock::systemDefaultZone)
			.withUserConfiguration(JwtTokenProvider.class);

		runner.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.jwt.secret=" + "a".repeat(31))
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("sniptext.jwt.secret=" + SECRET)
			.run(context -> assertThat(context).hasSingleBean(JwtTokenProvider.class));
	}

	private static String base64Url(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
