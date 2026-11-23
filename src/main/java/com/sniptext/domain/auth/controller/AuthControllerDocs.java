package com.sniptext.domain.auth.controller;

import org.springframework.http.ResponseEntity;

import com.sniptext.domain.auth.controller.dto.AuthLoginRequest;
import com.sniptext.domain.auth.controller.dto.AuthLoginResponse;
import com.sniptext.domain.auth.controller.dto.AuthReissueResponse;
import com.sniptext.domain.auth.controller.dto.AuthSignUpRequest;
import com.sniptext.domain.auth.controller.dto.AuthSignUpResponse;
import com.sniptext.global.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증 API의 스웨거 설명. 매핑·검증은 {@link AuthController}에 있다.
 * 자세한 규칙은 docs/s1-api-spec.md 1~4번이 기준이다.
 * 네 API 모두 접근 토큰 없이 호출하므로 빈 @SecurityRequirements로 전역 Bearer 요구(자물쇠)를 지운다.
 */
@Tag(name = "인증", description = "구글 로그인·약관 동의 후 가입·접근 토큰 재발급·로그아웃")
public interface AuthControllerDocs {

	@Operation(summary = "구글 로그인",
		description = "처음 온 사용자도 200이며, isCompletedSignUp이 false면 약관 동의 화면으로 보내 2번으로 가입합니다. "
			+ "가입된 회원이면 재발급 토큰을 쿠키로 주므로 credentials: 'include'로 호출합니다. "
			+ "AUTH502_1이면 같은 요청을 다시 보내지 말고 구글 로그인부터 다시 시작합니다 (인가 코드는 한 번만 쓸 수 있음).")
	@SecurityRequirements
	ResponseEntity<ApiResponse<AuthLoginResponse>> login(AuthLoginRequest request);

	// 실제 응답이 201이라 기본값(200) 대신 201로 표시한다. 이름이 같은 공통 응답 ApiResponse와 겹쳐 전체 이름으로 쓴다.
	@Operation(summary = "약관 동의 후 가입 완료",
		description = "1번에서 받은 가입용 토큰(30분)으로 회원을 만들고 바로 로그인 처리합니다. "
			+ "재발급 토큰을 쿠키로 주므로 credentials: 'include'로 호출합니다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공")
	@SecurityRequirements
	ResponseEntity<ApiResponse<AuthSignUpResponse>> signUp(AuthSignUpRequest request);

	@Operation(summary = "접근 토큰 재발급",
		description = "접근 토큰이 만료(COMMON401_1)됐거나 새로고침으로 사라졌을 때 쿠키만으로 호출합니다 (credentials: 'include'). "
			+ "재발급 토큰도 새것으로 바뀌므로 여러 탭이 동시에 부르지 말고 한 번만 보냅니다. 실패하면 쿠키를 지우니 다시 로그인합니다.")
	@SecurityRequirements
	ResponseEntity<ApiResponse<AuthReissueResponse>> reissue(
		@Parameter(description = "브라우저가 자동으로 보내는 재발급 토큰 쿠키. 화면에서 직접 넣지 않습니다") String refreshToken,
		HttpServletResponse servletResponse);

	@Operation(summary = "로그아웃",
		description = "접근 토큰 없이 쿠키만으로 처리하고(credentials: 'include'), 그 회원의 웹·확장 재발급 토큰을 모두 폐기한 뒤 쿠키를 지웁니다. "
			+ "쿠키가 없거나 이미 만료·폐기된 토큰이어도 성공입니다.")
	@SecurityRequirements
	ResponseEntity<ApiResponse<Void>> logout(
		@Parameter(description = "브라우저가 자동으로 보내는 재발급 토큰 쿠키. 화면에서 직접 넣지 않습니다") String refreshToken);

}
