package com.sniptext.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.user.controller.dto.UserUsageResponse;
import com.sniptext.domain.user.service.UserUsageQueryService;
import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.security.LoginUser;

import lombok.RequiredArgsConstructor;

/**
 * 회원 (로그인 필요, 캐시 금지).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserUsageQueryService userUsageQueryService;

	@GetMapping("/me/usage")
	public ResponseEntity<ApiResponse<UserUsageResponse>> getUsage(@LoginUser Long userId) {
		return ApiResponse.success(UserSuccessCode.USAGE_FOUND, UserUsageResponse.from(userUsageQueryService.getUsage(userId)));
	}

}
