package com.sniptext.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.auth.repository.RefreshTokenRepository;
import com.sniptext.domain.auth.repository.SocialAccountRepository;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.global.client.google.GoogleAuthCodeClient;
import com.sniptext.global.security.JwtTokenProvider;
import com.sniptext.support.MutableClock;

/** 만료 토큰 정리의 잠금 충돌 재시도와 입력 검사. 실제 삭제는 RefreshTokenCleanupTest가 MySQL로 확인한다. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupRetryTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 4, 0);

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

	@Test
	@DisplayName("한 묶음이 잠금 충돌로 실패하면 그 묶음을 다시 시도해 이어서 지운다")
	void retriesOnLockFailure() {
		given(refreshTokenRepository.deleteExpired(NOW, 2)).willReturn(1);
		given(transactionTemplate.execute(any()))
			.willThrow(new CannotAcquireLockException("deadlock"))
			.willAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));

		int deleted = authCommandService.deleteExpiredRefreshTokens(2);

		assertThat(deleted).isEqualTo(1);
		then(transactionTemplate).should(times(2)).execute(any());
	}

	@Test
	@DisplayName("같은 묶음이 세 번 연속 잠금 충돌이면 더 시도하지 않고 예외를 그대로 던진다")
	void givesUpAfterMaxAttempts() {
		given(transactionTemplate.execute(any())).willThrow(new CannotAcquireLockException("deadlock"));

		assertThatThrownBy(() -> authCommandService.deleteExpiredRefreshTokens(2))
			.isInstanceOf(CannotAcquireLockException.class);
		then(transactionTemplate).should(times(AuthCommandService.EXPIRED_TOKEN_DELETE_MAX_ATTEMPTS)).execute(any());
	}

	@Test
	@DisplayName("한 번에 지울 개수가 1보다 작으면 IllegalArgumentException이고 아무것도 지우지 않는다")
	void rejectsNonPositiveSize() {
		assertThatThrownBy(() -> authCommandService.deleteExpiredRefreshTokens(0))
			.isInstanceOf(IllegalArgumentException.class);
		then(refreshTokenRepository).should(never()).deleteExpired(any(), anyInt());
	}

}
