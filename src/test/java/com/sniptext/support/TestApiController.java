package com.sniptext.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

/**
 * 공통 설정 검증용 컨트롤러. 컴포넌트 스캔에서 빠지고 IntegrationTestSupport가 직접 등록한다.
 */
@TestComponent
@RestController
@RequestMapping("/api/v1")
public class TestApiController {

	@GetMapping("/test/ping")
	public ResponseEntity<ApiResponse<String>> ping() {
		return ApiResponse.success(TestSuccessCode.OK, "pong");
	}

	@PostMapping("/test/ping")
	public ResponseEntity<ApiResponse<String>> postPing() {
		return ApiResponse.success(TestSuccessCode.OK, "pong");
	}

	@GetMapping("/test/login-user")
	public ResponseEntity<ApiResponse<Long>> loginUser(@LoginUser Long userId) {
		return ApiResponse.success(TestSuccessCode.OK, userId);
	}

	// 공개 경로에서 인증 없이 @LoginUser를 쓰면 COMMON401_1인지 확인하는 용도
	@GetMapping("/gallery/test/login-user")
	public ResponseEntity<ApiResponse<Long>> galleryLoginUser(@LoginUser Long userId) {
		return ApiResponse.success(TestSuccessCode.OK, userId);
	}

	@GetMapping("/test/forbidden")
	public ResponseEntity<ApiResponse<Void>> forbidden() {
		throw new AccessDeniedException("권한 없음");
	}

	@GetMapping("/gallery/test/ping")
	public ResponseEntity<ApiResponse<String>> galleryPing() {
		return ApiResponse.success(TestSuccessCode.OK, "pong");
	}

	@PostMapping("/gallery/test/ping")
	public ResponseEntity<ApiResponse<String>> galleryPostPing() {
		return ApiResponse.success(TestSuccessCode.OK, "pong");
	}

	@PostMapping("/auth/test/ping")
	public ResponseEntity<ApiResponse<String>> authPing() {
		return ApiResponse.success(TestSuccessCode.OK, "pong");
	}

}
