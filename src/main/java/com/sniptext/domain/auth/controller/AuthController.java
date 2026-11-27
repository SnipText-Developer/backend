package com.sniptext.domain.auth.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.auth.controller.dto.AuthLoginRequest;
import com.sniptext.domain.auth.controller.dto.AuthLoginResponse;
import com.sniptext.domain.auth.controller.dto.AuthReissueResponse;
import com.sniptext.domain.auth.controller.dto.AuthSignUpRequest;
import com.sniptext.domain.auth.controller.dto.AuthSignUpResponse;
import com.sniptext.domain.auth.service.AuthCommandService;
import com.sniptext.domain.auth.service.RefreshTokenIssuer;
import com.sniptext.domain.auth.service.dto.command.AuthLogoutCommand;
import com.sniptext.domain.auth.service.dto.command.AuthReissueCommand;
import com.sniptext.domain.auth.service.dto.result.AuthLoginResult;
import com.sniptext.domain.auth.service.dto.result.AuthReissueResult;
import com.sniptext.domain.auth.service.dto.result.AuthSignUpResult;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.response.ResultCode;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 로그인·가입·재발급·로그아웃 (로그인 불필요, 캐시 금지). 재발급 토큰은 본문이 아니라 HttpOnly·Secure 쿠키로만 내보낸다.
 * 쿠키 경로를 인증 API로 좁혀 다른 API 요청에는 재발급 토큰이 실려 가지 않게 한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

	private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
	private static final String COOKIE_PATH = "/api/v1/auth";

	private final AuthCommandService authCommandService;
	private final AuthCookieProperties cookieProperties;

	@Override
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthLoginResponse>> login(@Valid @RequestBody AuthLoginRequest request) {
		AuthLoginResult result = authCommandService.login(request.toCommand());
		AuthLoginResponse response = AuthLoginResponse.from(result);

		if (!result.completedSignUp()) {
			return ResponseEntity.ok(new ApiResponse<>(true, AuthSuccessCode.LOGGED_IN.getCode(),
				AuthSuccessCode.SIGN_UP_REQUIRED_MESSAGE, response));
		}
		return withRefreshToken(AuthSuccessCode.LOGGED_IN, response, result.refreshToken());
	}

	@Override
	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<AuthSignUpResponse>> signUp(@Valid @RequestBody AuthSignUpRequest request) {
		AuthSignUpResult result = authCommandService.signUp(request.toCommand());

		return withRefreshToken(AuthSuccessCode.SIGNED_UP, AuthSignUpResponse.from(result), result.refreshToken());
	}

	/**
	 * 실패하면 응답은 전역 예외 처리가 만들고, 여기서는 다시 쓸 수 없는 쿠키를 지우는 헤더만 붙인다.
	 * 지우지 않으면 브라우저가 14일 동안 같은 쿠키로 새로고침마다 재발급을 다시 시도한다.
	 */
	@Override
	@PostMapping("/reissue")
	public ResponseEntity<ApiResponse<AuthReissueResponse>> reissue(
		@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken, HttpServletResponse servletResponse) {

		AuthReissueResult result;
		try {
			result = authCommandService.reissue(new AuthReissueCommand(refreshToken));
		} catch (BusinessException e) {
			servletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie("", Duration.ZERO).toString());
			throw e;
		}

		return withRefreshToken(AuthSuccessCode.TOKEN_REISSUED, AuthReissueResponse.from(result), result.refreshToken());
	}

	/** 접근 토큰 없이 쿠키만으로 처리한다. 쿠키가 없거나 이미 폐기된 토큰이어도 성공이고, 쿠키는 항상 지운다. */
	@Override
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Void>> logout(
		@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {

		authCommandService.logout(new AuthLogoutCommand(refreshToken));

		return withRefreshTokenCookie(AuthSuccessCode.LOGGED_OUT, null, refreshTokenCookie("", Duration.ZERO));
	}

	private <T> ResponseEntity<ApiResponse<T>> withRefreshToken(ResultCode resultCode, T response, String refreshToken) {
		return withRefreshTokenCookie(resultCode, response, refreshTokenCookie(refreshToken, RefreshTokenIssuer.TTL));
	}

	private <T> ResponseEntity<ApiResponse<T>> withRefreshTokenCookie(ResultCode resultCode, T response,
		ResponseCookie cookie) {
		return ResponseEntity.status(resultCode.getHttpStatus())
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(ApiResponse.of(resultCode, response));
	}

	// 브라우저는 이름·경로가 같은 쿠키를 같은 쿠키로 본다. 지울 때도 같은 속성에 유효 기간 0으로 내보낸다
	private ResponseCookie refreshTokenCookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
			.httpOnly(true)
			.secure(true)
			.sameSite(cookieProperties.sameSite())
			.path(COOKIE_PATH)
			.maxAge(maxAge)
			.build();
	}

}
