package com.sniptext.domain.auth.service;

import java.util.Optional;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.auth.entity.SocialAccount;
import com.sniptext.domain.auth.entity.SocialProvider;
import com.sniptext.domain.auth.exception.AuthErrorCode;
import com.sniptext.domain.auth.repository.SocialAccountRepository;
import com.sniptext.domain.auth.service.dto.command.AuthLoginCommand;
import com.sniptext.domain.auth.service.dto.command.AuthSignUpCommand;
import com.sniptext.domain.auth.service.dto.result.AuthLoginResult;
import com.sniptext.domain.auth.service.dto.result.AuthSignUpResult;
import com.sniptext.domain.user.service.UserCommandService;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.domain.user.service.dto.command.UserSignUpCommand;
import com.sniptext.global.client.google.GoogleAccount;
import com.sniptext.global.client.google.GoogleAuthCodeClient;
import com.sniptext.global.client.google.GoogleLoginException;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.security.JwtTokenProvider;
import com.sniptext.global.security.SignupTokenClaims;

import lombok.RequiredArgsConstructor;

/**
 * 구글 로그인과 가입 완료. 클래스에 @Transactional을 붙이지 않는다.
 * 구글 호출(최대 수 초) 동안 DB 연결을 잡고 있지 않도록, DB 작업만 TransactionTemplate으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class AuthCommandService {

	/** 같은 구글 계정·같은 이메일의 가입이 이미 있다는 뜻의 유니크 제약. 그 밖의 무결성 위반은 코드 실수라 500이다 */
	private static final Set<String> ALREADY_SIGNED_UP_UNIQUES = Set.of("uk_users_email", "uk_social_provider_user");

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

	/**
	 * 가입을 완료하고 바로 로그인 처리한다. 회원·소셜 계정·약관 동의·재발급 토큰을 한 트랜잭션으로 저장한다.
	 * 토큰이 만료·위조면 AUTH401_4, 이미 가입됐으면 AUTH409_1.
	 * 유니크 위반 뒤 트랜잭션은 롤백 전용이라, AUTH409_1 변환은 트랜잭션이 끝난 뒤 바깥에서 한다.
	 */
	public AuthSignUpResult signUp(AuthSignUpCommand command) {
		SignupTokenClaims claims = jwtTokenProvider.parseSignupToken(command.signupToken())
			.orElseThrow(() -> new BusinessException(AuthErrorCode.SIGNUP_TOKEN_INVALID));

		try {
			return transactionTemplate.execute(status -> {
				Long userId = userCommandService.signUp(
					new UserSignUpCommand(claims.email(), claims.name(), claims.profileImageUrl()));
				socialAccountRepository.save(SocialAccount.create(userId, SocialProvider.GOOGLE, claims.providerUserId()));
				String refreshToken = refreshTokenIssuer.issueForNewLogin(userId);
				return new AuthSignUpResult(jwtTokenProvider.issueAccessToken(userId), refreshToken, userId,
					userQueryService.getNickname(userId));
			});
		} catch (DataIntegrityViolationException e) {
			// 위반 예외 메시지에는 겹친 이메일이 들어 있으므로 원인으로 붙이거나 로그에 남기지 않는다
			if (isAlreadySignedUp(e)) {
				throw new BusinessException(AuthErrorCode.ALREADY_SIGNED_UP);
			}
			throw e;
		}
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

	private static boolean isAlreadySignedUp(DataIntegrityViolationException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation.getKind() == ConstraintKind.UNIQUE
					&& ALREADY_SIGNED_UP_UNIQUES.contains(withoutTablePrefix(violation.getConstraintName()));
			}
		}
		return false;
	}

	// MySQL은 제약 이름 앞에 테이블 이름을 붙여 알려 준다 (users.uk_users_email)
	private static String withoutTablePrefix(String constraintName) {
		if (constraintName == null) {
			return "";
		}
		return constraintName.substring(constraintName.lastIndexOf('.') + 1);
	}

}
