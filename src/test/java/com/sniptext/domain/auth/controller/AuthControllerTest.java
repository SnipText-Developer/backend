package com.sniptext.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;

import com.sniptext.domain.auth.entity.ClientType;
import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.auth.repository.SocialAccountRepository;
import com.sniptext.domain.auth.service.RefreshTokenIssuer;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.fixture.SocialAccountFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.client.google.GoogleAccount;
import com.sniptext.global.client.google.GoogleAuthCodeClient;
import com.sniptext.global.client.google.GoogleLoginException;
import com.sniptext.global.security.AccessTokenResult;
import com.sniptext.global.security.JwtTokenProvider;
import com.sniptext.global.security.SignupTokenClaims;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import tools.jackson.databind.json.JsonMapper;

/**
 * 명세 1번. 구글 호출만 가짜로 두고 필터·트랜잭션·MySQL까지 실제로 거친다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 호출자 트랜잭션 강제(MANDATORY)가 드러나지 않는다. 대신 회원을 직접 지운다
 * (소셜 계정·토큰은 외래키 CASCADE로 함께 지워진다).
 */
@Import(MutableClockConfiguration.class)
class AuthControllerTest extends IntegrationTestSupport {

	private static final String LOGIN_PATH = "/api/v1/auth/login";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 11, 5, 10, 0);
	private static final Pattern REFRESH_COOKIE = Pattern.compile("^refreshToken=([A-Za-z0-9_-]+);");
	private static final String CODE = "4/0AQSTgQ";
	private static final String VERIFIER = "pkce-verifier";
	private static final String REDIRECT_URI = "https://sniptext.io/auth/google/callback";
	private static final String SUB = "109876543210987654321";
	private static final String EMAIL = "new-user@gmail.com";
	private static final String NAME = "홍길동";
	private static final String PICTURE = "https://lh3.googleusercontent.com/a/sample";
	private static final GoogleAccount ACCOUNT = new GoogleAccount(SUB, EMAIL, NAME, PICTURE);

	@MockitoBean
	private GoogleAuthCodeClient googleAuthCodeClient;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private UserCommandService userCommandService;

	@Autowired
	private RefreshTokenIssuer refreshTokenIssuer;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JsonMapper jsonMapper;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
	}

	@AfterEach
	void tearDown() {
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("처음 온 사용자는 200과 가입용 토큰을 받고, 회원·쿠키는 만들어지지 않는다")
	void loginFirstTime() throws Exception {
		given(googleAuthCodeClient.exchange(CODE, VERIFIER, REDIRECT_URI)).willReturn(Optional.of(ACCOUNT));

		MvcResult result = login().andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("AUTH200_2"))
			.andExpect(jsonPath("$.message").value("약관 동의가 필요합니다."))
			.andExpect(jsonPath("$.result.isCompletedSignUp").value(false))
			.andExpect(jsonPath("$.result.accessToken").value(nullValue()))
			.andExpect(jsonPath("$.result.userId").value(nullValue()))
			.andExpect(jsonPath("$.result.nickname").value(nullValue()))
			.andExpect(jsonPath("$.result.email").value(EMAIL))
			.andExpect(jsonPath("$.result.name").value(NAME))
			.andExpect(jsonPath("$.result.profileImageUrl").value(PICTURE))
			.andReturn();

		assertThat(jwtTokenProvider.parseSignupToken(resultField(result, "signupToken")))
			.contains(new SignupTokenClaims(SUB, EMAIL, NAME, PICTURE));
		assertThat(userRepository.count()).isZero();
		assertThat(refreshTokenRepository.count()).isZero();
	}

	@Test
	@DisplayName("가입된 회원은 접근 토큰과 재발급 쿠키를 받고, 로그인 시각이 기록되며, 본문에 재발급 토큰이 없다")
	void loginSignedUp() throws Exception {
		Long userId = signedUpUser();
		given(googleAuthCodeClient.exchange(CODE, VERIFIER, REDIRECT_URI)).willReturn(Optional.of(ACCOUNT));

		MvcResult result = login().andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AUTH200_2"))
			.andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
			.andExpect(jsonPath("$.result.isCompletedSignUp").value(true))
			.andExpect(jsonPath("$.result.completedSignUp").doesNotExist())
			.andExpect(jsonPath("$.result.userId").value(userId))
			.andExpect(jsonPath("$.result.nickname").value(UserFixture.NICKNAME))
			.andExpect(jsonPath("$.result.signupToken").value(nullValue()))
			.andExpect(jsonPath("$.result.email").value(nullValue()))
			.andExpect(jsonPath("$.result.name").value(nullValue()))
			.andExpect(jsonPath("$.result.profileImageUrl").value(nullValue()))
			.andExpect(jsonPath("$.result.refreshToken").doesNotExist())
			.andReturn();

		assertThat(jwtTokenProvider.parseAccessToken(resultField(result, "accessToken")))
			.isEqualTo(new AccessTokenResult.Authenticated(userId));
		assertRefreshToken(result, userId);
		assertThat(userRepository.findById(userId).map(User::getLastLoginAt)).contains(FIXED);
	}

	@Test
	@DisplayName("재발급 쿠키는 HttpOnly·Secure·14일·인증 경로·SameSite=Lax이다")
	void refreshCookieAttributes() throws Exception {
		signedUpUser();
		given(googleAuthCodeClient.exchange(CODE, VERIFIER, REDIRECT_URI)).willReturn(Optional.of(ACCOUNT));

		String cookie = login().andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth",
			"Max-Age=" + Duration.ofDays(14).toSeconds());
	}

	@Test
	@DisplayName("구글이 거절하면 AUTH401_3이고 쿠키가 없다")
	void loginRejected() throws Exception {
		given(googleAuthCodeClient.exchange(CODE, VERIFIER, REDIRECT_URI)).willReturn(Optional.empty());

		login().andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andExpect(jsonPath("$.code").value("AUTH401_3"))
			.andExpect(jsonPath("$.message").value("구글 로그인 정보를 확인할 수 없어요. 다시 시도해주세요."))
			.andExpect(jsonPath("$.result").value(nullValue()));
	}

	@Test
	@DisplayName("구글 장애면 AUTH502_1이고 캐시하지 않는다")
	void loginGoogleUnavailable() throws Exception {
		given(googleAuthCodeClient.exchange(CODE, VERIFIER, REDIRECT_URI))
			.willThrow(new GoogleLoginException("구글 토큰 교환 통신 실패"));

		login().andExpect(status().isBadGateway())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.code").value("AUTH502_1"))
			.andExpect(jsonPath("$.message").value("일시적인 오류로 로그인하지 못했어요. 다시 시도해주세요."));
	}

	@Test
	@DisplayName("연결된 회원이 탈퇴했으면 USER404_1이고 토큰을 만들지 않는다")
	void loginDeletedUser() throws Exception {
		Long userId = userRepository.save(UserFixture.deletedUser(EMAIL, FIXED)).getId();
		socialAccountRepository.save(SocialAccountFixture.google(userId, SUB));
		given(googleAuthCodeClient.exchange(CODE, VERIFIER, REDIRECT_URI)).willReturn(Optional.of(ACCOUNT));

		login().andExpect(status().isNotFound())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andExpect(jsonPath("$.code").value("USER404_1"));

		assertThat(refreshTokenRepository.count()).isZero();
	}

	@ParameterizedTest
	@ValueSource(strings = {"code", "codeVerifier", "redirectUri"})
	@DisplayName("code·codeVerifier·redirectUri가 비면 COMMON400_1이고 구글을 부르지 않는다")
	void loginBlankValues(String blankField) throws Exception {
		String body = """
			{"code": "%s", "codeVerifier": "%s", "redirectUri": "%s", "provider": "GOOGLE"}
			""".formatted(blankField.equals("code") ? " " : CODE, blankField.equals("codeVerifier") ? " " : VERIFIER,
			blankField.equals("redirectUri") ? " " : REDIRECT_URI);

		postLogin(body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON400_1"));

		verify(googleAuthCodeClient, never()).exchange(anyString(), anyString(), anyString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"\"provider\": \"NAVER\"", "\"provider\": \"google\"", "\"provider\": null"})
	@DisplayName("provider가 GOOGLE이 아니거나 없으면 AUTH400_1이고 구글을 부르지 않는다")
	void loginUnsupportedProvider(String provider) throws Exception {
		postLogin("""
			{"code": "%s", "codeVerifier": "%s", "redirectUri": "%s", %s}
			""".formatted(CODE, VERIFIER, REDIRECT_URI, provider))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH400_1"));

		verify(googleAuthCodeClient, never()).exchange(anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("로그인 시각 기록과 재발급 토큰 저장은 트랜잭션 밖에서 부르면 IllegalTransactionStateException이다")
	void requiresTransaction() {
		Long userId = signedUpUser();

		assertThatThrownBy(() -> userCommandService.recordLogin(userId))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> refreshTokenIssuer.issueForNewLogin(userId))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(refreshTokenRepository.count()).isZero();
	}

	private Long signedUpUser() {
		Long userId = userRepository.save(UserFixture.user(EMAIL)).getId();
		socialAccountRepository.save(SocialAccountFixture.google(userId, SUB));
		return userId;
	}

	private ResultActions login() throws Exception {
		return postLogin("""
			{"code": "%s", "codeVerifier": "%s", "redirectUri": "%s", "provider": "GOOGLE"}
			""".formatted(CODE, VERIFIER, REDIRECT_URI));
	}

	private ResultActions postLogin(String body) throws Exception {
		return mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String resultField(MvcResult result, String field) throws Exception {
		return jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").get(field).asString();
	}

	/** 쿠키의 원문은 DB에 그대로 있지 않고, 그 SHA-256 해시가 14일 만료의 웹 토큰으로 저장돼 있어야 한다. */
	private void assertRefreshToken(MvcResult result, Long userId) throws Exception {
		Matcher matcher = REFRESH_COOKIE.matcher(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
		assertThat(matcher.find()).isTrue();
		String rawToken = matcher.group(1);
		String hash = HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));

		RefreshToken saved = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
		assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getClientType()).isEqualTo(ClientType.WEB);
		assertThat(saved.getFamilyId()).hasSize(RefreshToken.FAMILY_ID_LENGTH);
		assertThat(saved.getExpiresAt()).isEqualTo(FIXED.plusDays(14));
		assertThat(saved.isRevoked()).isFalse();
	}

}
