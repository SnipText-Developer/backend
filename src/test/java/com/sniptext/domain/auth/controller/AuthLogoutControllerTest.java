package com.sniptext.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.auth.service.RefreshTokenIssuer;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.RefreshTokenFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.servlet.http.Cookie;

/**
 * 명세 4번. 클래스에 @Transactional을 붙이지 않는다. 붙이면 폐기가 실제로 커밋되는지 드러나지 않는다.
 * 대신 회원을 직접 지운다 (토큰은 외래키 CASCADE로 함께 지워진다).
 */
@Import(MutableClockConfiguration.class)
class AuthLogoutControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/auth/logout";
	private static final String REISSUE_PATH = "/api/v1/auth/reissue";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 11, 6, 10, 0);

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private RefreshTokenIssuer refreshTokenIssuer;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private MutableClock clock;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = userRepository.save(UserFixture.user("logout@gmail.com")).getId();
	}

	@AfterEach
	void tearDown() {
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("로그아웃하면 AUTH200_1이고, 쿠키를 지우며 캐시하지 않는다")
	void logout() throws Exception {
		MvcResult result = logout(login()).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("AUTH200_1"))
			.andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
			.andExpect(jsonPath("$.result").value(nullValue()))
			.andReturn();

		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
			.startsWith("refreshToken=;")
			.contains("Max-Age=0", "Path=/api/v1/auth", "HttpOnly", "Secure", "SameSite=Lax");
	}

	@Test
	@DisplayName("로그아웃하면 그 회원의 재발급 토큰이 웹·확장 모두 폐기되어 다른 로그인의 토큰도 재발급할 수 없다")
	void revokesAllOfUser() throws Exception {
		String token = login();
		String otherLogin = login();
		refreshTokenRepository.save(RefreshTokenFixture.extension(userId, RefreshTokenFixture.hash(1), familyId()));

		logout(token).andExpect(status().isOk());

		assertThat(refreshTokenRepository.findAll()).hasSize(3).allMatch(RefreshToken::isRevoked);
		reissue(otherLogin).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH401_5"));
	}

	@Test
	@DisplayName("다른 회원의 재발급 토큰은 폐기하지 않는다")
	void keepsOtherUser() throws Exception {
		Long otherId = userRepository.save(UserFixture.user("other@gmail.com")).getId();
		String otherToken = login(otherId);

		logout(login()).andExpect(status().isOk());

		reissue(otherToken).andExpect(status().isOk());
	}

	@Test
	@DisplayName("쿠키가 없어도 AUTH200_1이고 쿠키를 지우며, 어떤 토큰도 폐기하지 않는다")
	void withoutCookie() throws Exception {
		login();

		mockMvc.perform(post(PATH)).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AUTH200_1"))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

		assertThat(refreshTokenRepository.findAll()).noneMatch(RefreshToken::isRevoked);
	}

	@Test
	@DisplayName("모르는 토큰이어도 AUTH200_1이고 어떤 토큰도 폐기하지 않는다")
	void unknownToken() throws Exception {
		login();

		logout("unknown-token").andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AUTH200_1"));

		assertThat(refreshTokenRepository.findAll()).noneMatch(RefreshToken::isRevoked);
	}

	@Test
	@DisplayName("이미 폐기된 토큰이면 AUTH200_1이고 쿠키를 지우지만, 그 회원의 다른 토큰은 폐기하지 않는다 (샌 옛 토큰으로 강제 로그아웃 방지)")
	void revokedToken() throws Exception {
		String oldToken = login();
		reissue(oldToken).andExpect(status().isOk());
		LocalDateTime firstRevokedAt = revokedAtOf(oldToken);
		String otherLogin = login();
		clock.advance(Duration.ofHours(1));

		logout(oldToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AUTH200_1"))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

		assertThat(refreshTokenRepository.findAll().stream().filter(token -> !token.isRevoked())).hasSize(2);
		assertThat(revokedAtOf(oldToken)).isEqualTo(firstRevokedAt);
		reissue(otherLogin).andExpect(status().isOk());
	}

	@Test
	@DisplayName("만료 1마이크로초 전의 토큰이면 그 회원의 토큰을 모두 폐기한다")
	void justBeforeExpiry() throws Exception {
		String token = login();
		clock.advance(Duration.ofDays(14).minusNanos(1_000));
		login();

		logout(token).andExpect(status().isOk());

		assertThat(refreshTokenRepository.findAll()).hasSize(2).allMatch(RefreshToken::isRevoked);
	}

	@Test
	@DisplayName("만료된 토큰이면 AUTH200_1이고 쿠키를 지우지만, 그 회원의 다른 토큰은 폐기하지 않는다")
	void expiredToken() throws Exception {
		String expired = login();
		clock.advance(Duration.ofDays(14));
		String otherLogin = login();

		logout(expired).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AUTH200_1"))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

		assertThat(refreshTokenRepository.findAll()).noneMatch(RefreshToken::isRevoked);
		reissue(otherLogin).andExpect(status().isOk());
	}

	@Test
	@DisplayName("탈퇴한 회원의 유효한 토큰이어도 AUTH200_1이고 쿠키를 지운다")
	void deletedUser() throws Exception {
		Long deletedId = userRepository.save(UserFixture.deletedUser("gone@gmail.com", FIXED.minusDays(1))).getId();

		logout(login(deletedId)).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AUTH200_1"))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
	}

	@Test
	@DisplayName("만료되거나 잘못된 접근 토큰을 함께 보내도 쿠키만으로 로그아웃된다")
	void ignoresAccessToken() throws Exception {
		String token = login();

		mockMvc.perform(post(PATH)
				.header(HttpHeaders.AUTHORIZATION, "Bearer invalid.access.token")
				.cookie(new Cookie("refreshToken", token)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("AUTH200_1"));

		assertThat(refreshTokenRepository.findAll()).allMatch(RefreshToken::isRevoked);
	}

	private String login() {
		return login(userId);
	}

	private String login(Long ownerId) {
		return transactionTemplate.execute(status -> refreshTokenIssuer.issueForNewLogin(ownerId));
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return mockMvc.perform(post(PATH).cookie(new Cookie("refreshToken", refreshToken)));
	}

	private ResultActions reissue(String refreshToken) throws Exception {
		return mockMvc.perform(post(REISSUE_PATH).cookie(new Cookie("refreshToken", refreshToken)));
	}

	private LocalDateTime revokedAtOf(String rawToken) {
		return refreshTokenRepository.findByTokenHash(RefreshTokenFixture.hashOf(rawToken)).orElseThrow().getRevokedAt();
	}

	private static String familyId() {
		return UUID.randomUUID().toString();
	}

}
