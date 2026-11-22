package com.sniptext.fixture;

import java.time.LocalDateTime;

import org.springframework.test.util.ReflectionTestUtils;

import com.sniptext.domain.user.entity.User;

/**
 * 테스트용 회원. 생성 인자가 바뀌면 여기만 고친다.
 * 탈퇴 시각은 아직 바꾸는 기능(탈퇴)이 없어서 필드를 직접 넣는다. 그 기능이 생기면 엔티티 메서드로 바꾼다.
 */
public final class UserFixture {

	public static final String EMAIL = "user@sniptext.com";
	public static final String NICKNAME = "스닙이";
	public static final String PROFILE_IMAGE_URL = "https://lh3.googleusercontent.com/a/sample";

	private UserFixture() {
	}

	public static User user() {
		return user(EMAIL);
	}

	public static User user(String email) {
		return User.create(email, NICKNAME, PROFILE_IMAGE_URL);
	}

	public static User userWithoutProfileImage(String email) {
		return User.create(email, NICKNAME, null);
	}

	public static User deletedUser(String email, LocalDateTime deletedAt) {
		User user = user(email);
		ReflectionTestUtils.setField(user, "deletedAt", deletedAt);
		return user;
	}

}
