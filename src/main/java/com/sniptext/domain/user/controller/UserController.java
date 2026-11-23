package com.sniptext.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.user.controller.dto.UserDetailResponse;
import com.sniptext.domain.user.controller.dto.UserUsageResponse;
import com.sniptext.domain.user.service.UserQueryService;
import com.sniptext.domain.user.service.UserUsageQueryService;
import com.sniptext.domain.user.service.dto.result.UserDetailResult;
import com.sniptext.domain.user.service.dto.result.UserUsageResult;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import lombok.RequiredArgsConstructor;

/**
 * 회원 (로그인 필요, 캐시 금지).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

	private final UserQueryService userQueryService;
	private final UserUsageQueryService userUsageQueryService;

	@Override
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserDetailResponse>> getMe(@LoginUser Long userId) {
		UserDetailResult result = userQueryService.getDetail(userId);

		return ApiResponse.success(UserSuccessCode.USER_FOUND, UserDetailResponse.from(result));
	}

	@Override
	@GetMapping("/me/usage")
	public ResponseEntity<ApiResponse<UserUsageResponse>> getUsage(@LoginUser Long userId) {
		UserUsageResult result = userUsageQueryService.getUsage(userId);

		return ApiResponse.success(UserSuccessCode.USAGE_FOUND, UserUsageResponse.from(result));
	}

}
