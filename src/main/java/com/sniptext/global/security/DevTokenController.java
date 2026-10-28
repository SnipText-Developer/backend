package com.sniptext.global.security;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.response.CommonSuccessCode;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 개발용 임시 토큰. 회원 번호만으로 접근 토큰을 발급한다 (명세 공통 규칙 1장).
 * <p>
 * 켜는 프로필만 나열한다. "!prod"로 쓰면 프로필 설정을 빠뜨린 서버에서 운영에 열린다.
 * /api/v1/auth/ 아래라 허용 경로이므로, 꺼진 환경에서는 401이 아니라 없는 경로(404)로 응답한다.
 * 회원 존재는 확인하지 않는다 (global은 domain을 부를 수 없다).
 */
@Profile({"local", "dev"})
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class DevTokenController {

	private final JwtTokenProvider jwtTokenProvider;

	@PostMapping("/dev-token")
	public ResponseEntity<ApiResponse<DevTokenResponse>> issue(@Valid @RequestBody DevTokenRequest request) {
		String accessToken = jwtTokenProvider.issueAccessToken(request.userId());
		return ApiResponse.success(CommonSuccessCode.DEV_TOKEN_ISSUED, new DevTokenResponse(accessToken));
	}

}
