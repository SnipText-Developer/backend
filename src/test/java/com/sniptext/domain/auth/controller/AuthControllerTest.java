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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;

import com.sniptext.domain.auth.entity.ClientType;
import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.auth.entity.SocialProvider;
import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.auth.repository.SocialAccountRepository;
import com.sniptext.domain.auth.service.RefreshTokenIssuer;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.domain.user.repository.UserTermsAgreementRepository;
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
 * 명세 1·2번. 구글 호출만 가짜로 두고 필터·트랜잭션·MySQL까지 실제로 거친다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 롤백·커밋 단계와 동시 가입이 드러나지 않는다. 대신 회원을 직접 지운다
 * (소셜 계정·약관·토큰은 외래키 CASCADE로 함께 지워진다).
 */
@Import(MutableClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest extends IntegrationTestSupport {

	private static final String LOGIN_PATH = "/api/v1/auth/login";
	private static final String SIGNUP_PATH = "/api/v1/auth/signup";
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
	private UserTermsAgreementRepository termsAgreementRepository;

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
		Long userId = signedUpUser(EMAIL, SUB);
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
		signedUpUser(EMAIL, SUB);
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

		postJson(LOGIN_PATH, body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON400_1"));

		verify(googleAuthCodeClient, never()).exchange(anyString(), anyString(), anyString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"\"provider\": \"NAVER\"", "\"provider\": \"google\"", "\"provider\": null"})
	@DisplayName("provider가 GOOGLE이 아니거나 없으면 AUTH400_1이고 구글을 부르지 않는다")
	void loginUnsupportedProvider(String provider) throws Exception {
		postJson(LOGIN_PATH, """
			{"code": "%s", "codeVerifier": "%s", "redirectUri": "%s", %s}
			""".formatted(CODE, VERIFIER, REDIRECT_URI, provider))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH400_1"));

		verify(googleAuthCodeClient, never()).exchange(anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("가입하면 201이고 회원·소셜 계정·약관 2건·재발급 토큰이 저장되며, 닉네임과 로그인 시각이 채워진다")
	void signUp() throws Exception {
		MvcResult result = signUp(signupToken(), true).andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("AUTH201_1"))
			.andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
			.andExpect(jsonPath("$.result.isCompletedSignUp").value(true))
			.andExpect(jsonPath("$.result.nickname").value(NAME))
			.andExpect(jsonPath("$.result.refreshToken").doesNotExist())
			.andReturn();

		User user = userRepository.findAll().getFirst();
		assertThat(user.getEmail()).isEqualTo(EMAIL);
		assertThat(user.getLastLoginAt()).isEqualTo(FIXED);
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, SUB)).isPresent();
		assertThat(termsAgreementRepository.count()).isEqualTo(2);
		assertThat(jwtTokenProvider.parseAccessToken(resultField(result, "accessToken")))
			.isEqualTo(new AccessTokenResult.Authenticated(user.getId()));
		assertRefreshToken(result, user.getId());
	}

	@Test
	@DisplayName("가입 후 같은 구글 계정으로 로그인하면 바로 로그인된다")
	void loginAfterSignUp() throws Exception {
		signUp(signupToken(), true).andExpect(status().isCreated());
		given(googleAuthCodeClient.exchange(CODE, VERIFIER, REDIRECT_URI)).willReturn(Optional.of(ACCOUNT));

		login().andExpect(jsonPath("$.result.isCompletedSignUp").value(true))
			.andExpect(jsonPath("$.result.nickname").value(NAME));
	}

	@Test
	@DisplayName("termsAgreed가 false이거나 없으면 AUTH400_3이고 저장하지 않는다")
	void signUpTermsNotAgreed() throws Exception {
		signUp(signupToken(), false).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH400_3"));
		postJson(SIGNUP_PATH, """
			{"signupToken": "%s"}
			""".formatted(signupToken()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("AUTH400_3"));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	@DisplayName("가입용 토큰이 비면 COMMON400_1이다")
	void signUpBlankToken() throws Exception {
		signUp(" ", true).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMMON400_1"));
	}

	@Test
	@DisplayName("가입용 토큰이 위조이거나 접근 토큰이면 AUTH401_4이다")
	void signUpInvalidToken() throws Exception {
		signUp("not-a-token", true).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH401_4"));
		signUp(jwtTokenProvider.issueAccessToken(1L), true).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH401_4"))
			.andExpect(jsonPath("$.message").value("가입 시간이 지났어요. 다시 로그인해주세요."));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	@DisplayName("가입용 토큰이 30분 지나면 AUTH401_4이다")
	void signUpExpiredToken() throws Exception {
		String token = signupToken();
		clock.advance(Duration.ofMinutes(30));

		signUp(token, true).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH401_4"));
	}

	@Test
	@DisplayName("같은 가입용 토큰으로 두 번 가입하면 AUTH409_1이고 500·롤백 예외로 새지 않으며 이메일을 로그에 남기지 않는다")
	void signUpTwice(CapturedOutput output) throws Exception {
		String token = signupToken();
		signUp(token, true).andExpect(status().isCreated());

		signUp(token, true).andExpect(status().isConflict())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andExpect(jsonPath("$.code").value("AUTH409_1"))
			.andExpect(jsonPath("$.message").value("이미 가입된 계정이에요."));

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
		assertThat(output.getOut()).doesNotContain(EMAIL);
	}

	@Test
	@DisplayName("소셜 계정 저장이 유니크 위반이면 AUTH409_1이고 먼저 저장한 회원·약관도 함께 취소된다")
	void signUpRollsBackTogether() throws Exception {
		signedUpUser("other@gmail.com", SUB);

		signUp(signupToken(), true).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUTH409_1"));

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(termsAgreementRepository.count()).isZero();
	}

	@Test
	@DisplayName("동시에 같은 가입을 두 번 요청해도 하나만 201이고 나머지는 AUTH409_1이다")
	void signUpConcurrently() throws Exception {
		String token = signupToken();

		List<Integer> statuses = runConcurrently(() -> signUp(token, true).andReturn().getResponse().getStatus());

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(socialAccountRepository.count()).isEqualTo(1);
		assertThat(termsAgreementRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("로그인 시각 기록과 재발급 토큰 저장은 트랜잭션 밖에서 부르면 IllegalTransactionStateException이다")
	void requiresTransaction() {
		Long userId = signedUpUser(EMAIL, SUB);

		assertThatThrownBy(() -> userCommandService.recordLogin(userId))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> refreshTokenIssuer.issueForNewLogin(userId))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(refreshTokenRepository.count()).isZero();
	}

	private Long signedUpUser(String email, String sub) {
		Long userId = userRepository.save(UserFixture.user(email)).getId();
		socialAccountRepository.save(SocialAccountFixture.google(userId, sub));
		return userId;
	}

	private String signupToken() {
		return jwtTokenProvider.issueSignupToken(new SignupTokenClaims(SUB, EMAIL, NAME, PICTURE));
	}

	private ResultActions login() throws Exception {
		return postJson(LOGIN_PATH, """
			{"code": "%s", "codeVerifier": "%s", "redirectUri": "%s", "provider": "GOOGLE"}
			""".formatted(CODE, VERIFIER, REDIRECT_URI));
	}

	private ResultActions signUp(String token, boolean termsAgreed) throws Exception {
		return postJson(SIGNUP_PATH, """
			{"signupToken": "%s", "termsAgreed": %s}
			""".formatted(token, termsAgreed));
	}

	private ResultActions postJson(String path, String body) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
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

	private static List<Integer> runConcurrently(Callable<Integer> task) throws Exception {
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch start = new CountDownLatch(1);
			Callable<Integer> waiting = () -> {
				start.await();
				return task.call();
			};
			Future<Integer> first = executor.submit(waiting);
			Future<Integer> second = executor.submit(waiting);
			start.countDown();
			return List.of(first.get(), second.get());
		}
	}

}
