package com.sniptext.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.auth.entity.ClientType;
import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.auth.service.RefreshTokenIssuer;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.security.AccessTokenResult;
import com.sniptext.global.security.JwtTokenProvider;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/**
 * 명세 3번. 클래스에 @Transactional을 붙이지 않는다. 붙이면 계보 폐기의 커밋과 동시 요청이 드러나지 않는다.
 * 대신 회원을 직접 지운다 (토큰은 외래키 CASCADE로 함께 지워진다).
 */
@Import(MutableClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthReissueControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/auth/reissue";
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 11, 6, 10, 0);
	/** 같은 이름·경로에 유효 기간 0으로 내려 브라우저의 재발급 쿠키를 지운다 */
	private static final org.hamcrest.Matcher<String> CLEARED_COOKIE = allOf(startsWith("refreshToken=;"), containsString("Max-Age=0"),
		containsString("Path=/api/v1/auth"), containsString("HttpOnly"), containsString("Secure"), containsString("SameSite=Lax"));
	private static final Pattern REFRESH_COOKIE = Pattern.compile("^refreshToken=([A-Za-z0-9_-]+);");

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private RefreshTokenIssuer refreshTokenIssuer;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private MutableClock clock;

	@Autowired
	private JsonMapper jsonMapper;

	private Long userId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = userRepository.save(UserFixture.user("reissue@gmail.com")).getId();
	}

	@AfterEach
	void tearDown() {
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("재발급하면 접근 토큰과 새 쿠키를 받고, 이전 토큰은 폐기되며 새 토큰은 같은 계보로 14일 유효하다")
	void reissue() throws Exception {
		String oldToken = login();
		clock.advance(Duration.ofDays(1));

		MvcResult result = reissue(oldToken).andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.code").value("AUTH200_3"))
			.andExpect(jsonPath("$.message").value("토큰이 재발급되었습니다."))
			.andExpect(jsonPath("$.result.refreshToken").doesNotExist())
			.andReturn();

		String accessToken = jsonMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.get("result").get("accessToken").asString();
		assertThat(jwtTokenProvider.parseAccessToken(accessToken)).isEqualTo(new AccessTokenResult.Authenticated(userId));
		assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
			.contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth", "Max-Age=" + Duration.ofDays(14).toSeconds());
		assertThat(cookieToken(result)).isNotEqualTo(oldToken);

		List<RefreshToken> tokens = refreshTokenRepository.findAll(Sort.by("id"));
		assertThat(tokens).hasSize(2);
		assertThat(tokens.getFirst().getFamilyId()).isEqualTo(tokens.getLast().getFamilyId());
		assertThat(tokens.getFirst().isRevoked()).isTrue();
		assertThat(tokens.getLast().isRevoked()).isFalse();
		assertThat(tokens.getLast().getClientType()).isEqualTo(ClientType.WEB);
		assertThat(tokens.getLast().getExpiresAt()).isEqualTo(FIXED.plusDays(15));
	}

	@Test
	@DisplayName("새 토큰으로 다시 재발급할 수 있다")
	void reissueChain() throws Exception {
		String second = cookieToken(reissue(login()).andExpect(status().isOk()).andReturn());

		reissue(second).andExpect(status().isOk());
	}

	@Test
	@DisplayName("쿠키가 없으면 AUTH401_5이고 쿠키를 지우는 헤더를 내려준다")
	void withoutCookie() throws Exception {
		mockMvc.perform(post(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, CLEARED_COOKIE))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.code").value("AUTH401_5"))
			.andExpect(jsonPath("$.message").value("다시 로그인해주세요."))
			.andExpect(jsonPath("$.result").value(nullValue()));
	}

	@Test
	@DisplayName("모르는 토큰이면 AUTH401_5이고 쿠키를 지운다")
	void unknownToken() throws Exception {
		reissue("unknown-token").andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, CLEARED_COOKIE))
			.andExpect(jsonPath("$.code").value("AUTH401_5"));
	}

	@Test
	@DisplayName("탈퇴한 회원의 토큰이면 USER404_1이고 쿠키를 지우며, 토큰을 폐기하거나 새로 만들지 않는다")
	void deletedUser() throws Exception {
		Long deletedId = userRepository.save(UserFixture.deletedUser("gone@gmail.com", FIXED.minusDays(1))).getId();
		String token = transactionTemplate.execute(status -> refreshTokenIssuer.issueForNewLogin(deletedId));

		reissue(token).andExpect(status().isNotFound())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, CLEARED_COOKIE))
			.andExpect(jsonPath("$.code").value("USER404_1"))
			.andExpect(jsonPath("$.result").value(nullValue()));

		assertThat(refreshTokenRepository.findAll()).singleElement().matches(saved -> !saved.isRevoked());
	}

	@Test
	@DisplayName("만료 1마이크로초 전에는 재발급되고, 만료 시각부터는 AUTH401_5이며 계보를 폐기하지 않는다")
	void expiryBoundary() throws Exception {
		String beforeExpiry = login();
		String atExpiry = login();
		clock.advance(Duration.ofDays(14).minusNanos(1_000));
		reissue(beforeExpiry).andExpect(status().isOk());

		clock.advance(Duration.ofNanos(1_000));
		reissue(atExpiry).andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, CLEARED_COOKIE))
			.andExpect(jsonPath("$.code").value("AUTH401_5"));

		assertThat(refreshTokenRepository.findAll().stream().filter(token -> !token.isRevoked()))
			.hasSize(2);
	}

	@Test
	@DisplayName("이미 쓴 토큰이 다시 오면 AUTH401_5이고 그 계보 전체가 폐기되어 새 토큰도 못 쓰며, 폐기는 커밋되고 토큰 값 없이 로그에 남는다")
	void reuseRevokesFamily(CapturedOutput output) throws Exception {
		String oldToken = login();
		String newToken = cookieToken(reissue(oldToken).andReturn());

		reissue(oldToken).andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, CLEARED_COOKIE))
			.andExpect(jsonPath("$.code").value("AUTH401_5"));

		assertThat(refreshTokenRepository.findAll()).hasSize(2).allMatch(RefreshToken::isRevoked);
		reissue(newToken).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH401_5"));
		assertThat(refreshTokenRepository.count()).isEqualTo(2);
		assertThat(output.getAll()).contains("폐기된 재발급 토큰으로 재발급 시도(재사용 또는 로그아웃 뒤 요청), 계보 폐기: userId=" + userId)
			.doesNotContain(oldToken, newToken);
	}

	@Test
	@DisplayName("재사용 감지는 다른 로그인의 토큰을 폐기하지 않는다")
	void reuseKeepsOtherFamily() throws Exception {
		String oldToken = login();
		String otherLogin = login();
		reissue(oldToken).andExpect(status().isOk());

		reissue(oldToken).andExpect(status().isUnauthorized());

		reissue(otherLogin).andExpect(status().isOk());
	}

	@Test
	@DisplayName("같은 토큰으로 동시에 요청하면 하나만 성공하고, 재사용 감지로 계보 전체가 폐기되어 새 토큰도 못 쓴다")
	void concurrentReuse() throws Exception {
		String token = login();

		List<MvcResult> results = runConcurrently(() -> reissue(token).andReturn());

		assertThat(results.stream().map(result -> result.getResponse().getStatus())).containsExactlyInAnyOrder(200, 401);
		assertThat(refreshTokenRepository.findAll()).hasSize(2).allMatch(RefreshToken::isRevoked);
		MvcResult winner = results.stream().filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
		reissue(cookieToken(winner)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("재발급 토큰 저장은 트랜잭션 밖에서 부르면 IllegalTransactionStateException이다")
	void issueRequiresTransaction() {
		assertThatThrownBy(() -> refreshTokenIssuer.issueForReissue(userId, ClientType.WEB, "family"))
			.isInstanceOf(IllegalTransactionStateException.class);
	}

	private String login() {
		return transactionTemplate.execute(status -> refreshTokenIssuer.issueForNewLogin(userId));
	}

	private ResultActions reissue(String refreshToken) throws Exception {
		return mockMvc.perform(post(PATH).cookie(new Cookie("refreshToken", refreshToken)));
	}

	private static String cookieToken(MvcResult result) {
		Matcher matcher = REFRESH_COOKIE.matcher(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private static List<MvcResult> runConcurrently(Callable<MvcResult> task) throws Exception {
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch start = new CountDownLatch(1);
			Callable<MvcResult> waiting = () -> {
				start.await();
				return task.call();
			};
			Future<MvcResult> first = executor.submit(waiting);
			Future<MvcResult> second = executor.submit(waiting);
			start.countDown();
			return List.of(first.get(), second.get());
		}
	}

}
