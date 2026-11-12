package com.sniptext.domain.user.controller.dto;

import com.sniptext.domain.auth.entity.SocialProvider;
import com.sniptext.domain.user.service.dto.result.UserDetailResult;

/** 내 정보 (명세 5번). */
public record UserDetailResponse(
	Long userId,
	String nickname,
	String email,
	String profileImageUrl,
	SocialProvider loginType
) {

	public static UserDetailResponse from(UserDetailResult result) {
		return new UserDetailResponse(
			result.userId(),
			result.nickname(),
			result.email(),
			result.profileImageUrl(),
			result.loginType()
		);
	}

}
