package com.sniptext.domain.user.service.dto.result;

import com.sniptext.domain.auth.entity.SocialProvider;
import com.sniptext.domain.user.entity.User;

/**
 * 내 정보. 프로필 이미지가 없으면 profileImageUrl은 null이다.
 * S1 로그인은 구글뿐이라 loginType은 GOOGLE로 고정한다. 제공자가 늘면 소셜 계정에서 읽도록 바꾼다.
 */
public record UserDetailResult(Long userId, String nickname, String email, String profileImageUrl,
	SocialProvider loginType) {

	public static UserDetailResult from(User user) {
		return new UserDetailResult(user.getId(), user.getNickname(), user.getEmail(), user.getProfileImageUrl(),
			SocialProvider.GOOGLE);
	}

}
