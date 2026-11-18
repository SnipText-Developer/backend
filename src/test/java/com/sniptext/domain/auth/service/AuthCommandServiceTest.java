package com.sniptext.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.auth.entity.ClientType;
import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.auth.repository.SocialAccountRepository;
import com.sniptext.domain.auth.service.dto.command.AuthLogoutCommand;
import com.sniptext.domain.auth.service.dto.command.AuthReissueCommand;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.fixture.RefreshTokenFixture;
import com.sniptext.global.client.google.GoogleAuthCodeClient;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.security.JwtTokenProvider;
import com.sniptext.support.MutableClock;

/** 재발급·로그아웃의 순서 규칙. 실제 잠금·동시 요청은 컨트롤러 통합 테스트가 MySQL로 확인한다. */
@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long TOKEN_ID = 7L;
	private static final String RAW_TOKEN = "raw-refresh-token";
	private static final LocalDateTime NOW = RefreshTokenFixture.EXPIRES_AT.minusDays(1);

	@Mock
	private GoogleAuthCodeClient googleAuthCodeClient;

	@Mock
	private SocialAccountRepository socialAccountRepository;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Spy
	private Clock clock = new MutableClock(NOW);

	@Mock
	private UserCommandService userCommandService;

	@Mock
	private UserQueryService userQueryService;

	@Mock
	private RefreshTokenIssuer refreshTokenIssuer;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private TransactionTemplate transactionTemplate;

	@InjectMocks
	private AuthCommandService authCommandService;

	@BeforeEach
	void setUp() {
		RefreshToken token = RefreshTokenFixture.web(USER_ID, RefreshTokenIssuer.hash(RAW_TOKEN));
		ReflectionTestUtils.setField(token, "id", TOKEN_ID);
		given(refreshTokenRepository.findByTokenHash(RefreshTokenIssuer.hash(RAW_TOKEN))).willReturn(Optional.of(token));
		// 재발급은 execute, 로그아웃은 executeWithoutResult를 쓰므로 테스트마다 둘 중 하나만 불린다
		lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
			invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
		lenient().doAnswer(invocation -> {
			invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
	}

	@Test
	@DisplayName("재발급은 회원 행을 먼저 잠근 뒤 이전 토큰을 폐기하고 새 토큰을 저장한다")
	void locksUserFirst() {
		given(refreshTokenRepository.revokeIfActive(TOKEN_ID, NOW)).willReturn(1);

		authCommandService.reissue(new AuthReissueCommand(RAW_TOKEN));

		InOrder inOrder = inOrder(userCommandService, refreshTokenRepository, refreshTokenIssuer);
		inOrder.verify(userCommandService).lockUser(USER_ID);
		inOrder.verify(refreshTokenRepository).revokeIfActive(TOKEN_ID, NOW);
		inOrder.verify(refreshTokenIssuer).issueForReissue(USER_ID, ClientType.WEB, RefreshTokenFixture.FAMILY_ID);
	}

	@Test
	@DisplayName("탈퇴한 회원이면 USER404_1이고 토큰을 폐기하거나 새로 만들지 않는다")
	void deletedUser() {
		willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND)).given(userCommandService).lockUser(USER_ID);

		assertThatThrownBy(() -> authCommandService.reissue(new AuthReissueCommand(RAW_TOKEN)))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(UserErrorCode.USER_NOT_FOUND);
		then(refreshTokenRepository).should(never()).revokeIfActive(anyLong(), any());
		then(refreshTokenRepository).should(never()).revokeFamily(any(), any());
		then(refreshTokenIssuer).should(never()).issueForReissue(any(), any(), any());
	}

	@Test
	@DisplayName("로그아웃은 회원 행을 먼저 잠근 뒤 그 회원의 토큰을 모두 폐기한다")
	void logoutLocksUserFirst() {
		authCommandService.logout(new AuthLogoutCommand(RAW_TOKEN));

		InOrder inOrder = inOrder(userCommandService, refreshTokenRepository);
		inOrder.verify(userCommandService).lockUser(USER_ID);
		inOrder.verify(refreshTokenRepository).revokeAllByUserId(USER_ID, NOW);
	}

	@Test
	@DisplayName("탈퇴한 회원이면 로그아웃은 폐기 없이 성공한다")
	void logoutDeletedUser() {
		willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND)).given(userCommandService).lockUser(USER_ID);

		assertThatCode(() -> authCommandService.logout(new AuthLogoutCommand(RAW_TOKEN))).doesNotThrowAnyException();
		then(refreshTokenRepository).should(never()).revokeAllByUserId(any(), any());
	}

}
