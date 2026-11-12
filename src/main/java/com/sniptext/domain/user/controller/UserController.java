package com.sniptext.domain.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sniptext.domain.user.controller.dto.UserDetailResponse;
import com.sniptext.domain.user.service.UserQueryService;
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

	private final UserQueryService userQueryService;

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserDetailResponse>> getMe(@LoginUser Long userId) {
		return ApiResponse.success(UserSuccessCode.USER_FOUND, UserDetailResponse.from(userQueryService.getDetail(userId)));
	}

}
