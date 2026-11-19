package com.sniptext.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import java.time.LocalDateTime;
import java.util.Optional;

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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.auth.entity.ClientType;
import com.sniptext.domain.auth.entity.RefreshToken;
import com.sniptext.domain.auth.exception.AuthErrorCode;
import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.auth.repository.SocialAccountRepository;
import com.sniptext.domain.auth.service.dto.command.AuthReissueCommand;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.fixture.RefreshTokenFixture;
import com.sniptext.global.client.google.GoogleAuthCodeClient;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.security.JwtTokenProvider;
import com.sniptext.support.MutableClock;

/** 재발급의 순서 규칙. 실제 잠금·동시 요청은 AuthReissueControllerTest가 MySQL로 확인한다. */
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
	private MutableClock clock = new MutableClock(NOW);

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
		// 잠금 뒤 다시 읽는 토큰. 회원이 없으면 잠금에서 끝나 불리지 않는 테스트도 있다
		lenient().when(refreshTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
		given(transactionTemplate.execute(any())).willAnswer(invocation ->
			invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
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
	@DisplayName("회원 잠금을 기다리는 사이 만료되면 AUTH401_5이고 토큰을 폐기하거나 새로 만들지 않는다")
	void expiresWhileWaitingForLock() {
		willAnswer(invocation -> {
			clock.setTo(RefreshTokenFixture.EXPIRES_AT);
			return null;
		}).given(userCommandService).lockUser(USER_ID);

		assertThatThrownBy(() -> authCommandService.reissue(new AuthReissueCommand(RAW_TOKEN)))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
		then(refreshTokenRepository).should(never()).revokeIfActive(anyLong(), any());
		then(refreshTokenRepository).should(never()).revokeFamily(any(), any());
		then(refreshTokenIssuer).should(never()).issueForReissue(any(), any(), any());
	}

	@Test
	@DisplayName("잠금을 기다리는 사이 다른 요청이 토큰을 교체했으면 그 사이 만료됐어도 계보 전체를 폐기한다")
	void rotatedAndExpiredWhileWaitingForLock() {
		RefreshToken rotated = RefreshTokenFixture.web(USER_ID, RefreshTokenIssuer.hash(RAW_TOKEN));
		ReflectionTestUtils.setField(rotated, "id", TOKEN_ID);
		rotated.revoke(NOW);
		given(refreshTokenRepository.findById(TOKEN_ID)).willReturn(Optional.of(rotated));
		willAnswer(invocation -> {
			clock.setTo(RefreshTokenFixture.EXPIRES_AT);
			return null;
		}).given(userCommandService).lockUser(USER_ID);

		assertThatThrownBy(() -> authCommandService.reissue(new AuthReissueCommand(RAW_TOKEN)))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(AuthErrorCode.REFRESH_TOKEN_INVALID);
		then(refreshTokenRepository).should().revokeFamily(RefreshTokenFixture.FAMILY_ID, RefreshTokenFixture.EXPIRES_AT);
		then(refreshTokenIssuer).should(never()).issueForReissue(any(), any(), any());
	}

}
