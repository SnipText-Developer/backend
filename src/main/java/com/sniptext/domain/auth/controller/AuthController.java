package com.sniptext.domain.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.auth.controller.dto.AuthLoginRequest;
import com.sniptext.domain.auth.controller.dto.AuthLoginResponse;
import com.sniptext.domain.auth.service.AuthCommandService;
import com.sniptext.domain.auth.service.RefreshTokenIssuer;
import com.sniptext.domain.auth.service.dto.result.AuthLoginResult;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.response.ResultCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 로그인 (로그인 불필요, 캐시 금지). 재발급 토큰은 본문이 아니라 HttpOnly·Secure 쿠키로만 내보낸다.
 * 쿠키 경로를 인증 API로 좁혀 다른 API 요청에는 재발급 토큰이 실려 가지 않게 한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
	private static final String COOKIE_PATH = "/api/v1/auth";

	private final AuthCommandService authCommandService;
	private final AuthCookieProperties cookieProperties;

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

	private <T> ResponseEntity<ApiResponse<T>> withRefreshToken(ResultCode resultCode, T response, String refreshToken) {
		ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
			.httpOnly(true)
			.secure(true)
			.sameSite(cookieProperties.sameSite())
			.path(COOKIE_PATH)
			.maxAge(RefreshTokenIssuer.TTL)
			.build();
		return ResponseEntity.status(resultCode.getHttpStatus())
			.header(HttpHeaders.SET_COOKIE, cookie.toString())
			.body(ApiResponse.of(resultCode, response));
	}

}
