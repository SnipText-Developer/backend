package com.sniptext.domain.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.auth.entity.SocialAccount;
import com.sniptext.domain.auth.entity.SocialProvider;
import com.sniptext.domain.auth.exception.AuthErrorCode;
import com.sniptext.domain.auth.repository.SocialAccountRepository;
import com.sniptext.domain.auth.service.dto.command.AuthLoginCommand;
import com.sniptext.domain.auth.service.dto.result.AuthLoginResult;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.global.client.google.GoogleAccount;
import com.sniptext.global.client.google.GoogleAuthCodeClient;
import com.sniptext.global.client.google.GoogleLoginException;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.security.JwtTokenProvider;
import com.sniptext.global.security.SignupTokenClaims;

import lombok.RequiredArgsConstructor;

/**
 * 구글 로그인. 클래스에 @Transactional을 붙이지 않는다.
 * 구글 호출(최대 수 초) 동안 DB 연결을 잡고 있지 않도록, DB 작업만 TransactionTemplate으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class AuthCommandService {

	private final GoogleAuthCodeClient googleAuthCodeClient;
	private final SocialAccountRepository socialAccountRepository;
	private final UserCommandService userCommandService;
	private final UserQueryService userQueryService;
	private final RefreshTokenIssuer refreshTokenIssuer;
	private final JwtTokenProvider jwtTokenProvider;
	private final TransactionTemplate transactionTemplate;

	/**
	 * 구글 인가 코드로 로그인한다. 사용자 실패는 AUTH401_3, 구글 장애는 AUTH502_1.
	 * 처음 온 사용자는 회원을 만들지 않고 가입용 토큰만 준다 (약관 동의 전에는 개인정보를 저장하지 않는다).
	 * 연결된 회원이 탈퇴했으면 USER404_1이다.
	 */
	public AuthLoginResult login(AuthLoginCommand command) {
		GoogleAccount account = exchangeCode(command);

		Optional<SocialAccount> socialAccount = socialAccountRepository.findByProviderAndProviderUserId(
			SocialProvider.GOOGLE, account.subject());
		if (socialAccount.isEmpty()) {
			String signupToken = jwtTokenProvider.issueSignupToken(new SignupTokenClaims(
				account.subject(), account.email(), account.name(), account.pictureUrl()));
			return AuthLoginResult.signUpRequired(signupToken, account.email(), account.name(), account.pictureUrl());
		}

		Long userId = socialAccount.get().getUserId();
		return transactionTemplate.execute(status -> {
			userCommandService.recordLogin(userId);
			String refreshToken = refreshTokenIssuer.issueForNewLogin(userId);
			return AuthLoginResult.loggedIn(jwtTokenProvider.issueAccessToken(userId), refreshToken, userId,
				userQueryService.getNickname(userId));
		});
	}

	// 구글 호출 실패 원인에는 응답 내용이 들어 있을 수 있어 원인으로 붙이지 않는다
	private GoogleAccount exchangeCode(AuthLoginCommand command) {
		try {
			return googleAuthCodeClient.exchange(command.code(), command.codeVerifier(), command.redirectUri())
				.orElseThrow(() -> new BusinessException(AuthErrorCode.GOOGLE_LOGIN_REJECTED));
		} catch (GoogleLoginException e) {
			throw new BusinessException(AuthErrorCode.GOOGLE_UNAVAILABLE);
		}
	}

}
