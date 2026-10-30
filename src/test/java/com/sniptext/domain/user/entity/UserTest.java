package com.sniptext.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

	private static final String EMAIL = "user@sniptext.com";
	private static final String NICKNAME = "스닙이";
	private static final String PROFILE = "https://lh3.googleusercontent.com/a/sample";

	@Test
	@DisplayName("회원을 만들면 받은 값이 그대로 들어가고 정렬은 최근 사용순, 플랜 지정값·탈퇴 시각은 없다")
	void create() {
		User user = User.create(EMAIL, NICKNAME, PROFILE);

		assertThat(user.getEmail()).isEqualTo(EMAIL);
		assertThat(user.getNickname()).isEqualTo(NICKNAME);
		assertThat(user.getProfileImageUrl()).isEqualTo(PROFILE);
		assertThat(user.getTemplateSort()).isEqualTo(TemplateSort.RECENT_USED);
		assertThat(user.getPlanOverride()).isNull();
		assertThat(user.getLastLoginAt()).isNull();
		assertThat(user.getDeletedAt()).isNull();
	}

	@Test
	@DisplayName("프로필 이미지가 없어도 만들 수 있다")
	void createWithoutProfileImage() {
		User user = User.create(EMAIL, NICKNAME, null);

		assertThat(user.getProfileImageUrl()).isNull();
	}

	@Test
	@DisplayName("경계 길이(이메일 255자, 닉네임 50자, 프로필 이미지 500자)는 만들 수 있다")
	void createAtMaxLength() {
		String email = "a".repeat(User.EMAIL_MAX_LENGTH - "@x.com".length()) + "@x.com";
		String nickname = "가".repeat(User.NICKNAME_MAX_LENGTH);
		String profile = "https://x.com/" + "a".repeat(User.PROFILE_IMAGE_URL_MAX_LENGTH - "https://x.com/".length());

		User user = User.create(email, nickname, profile);

		assertThat(user.getEmail()).hasSize(255);
		assertThat(user.getNickname()).hasSize(50);
		assertThat(user.getProfileImageUrl()).hasSize(500);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t"})
	@DisplayName("이메일이 없거나 공백이면 IllegalArgumentException")
	void blankEmail(String email) {
		assertThatThrownBy(() -> User.create(email, NICKNAME, PROFILE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t"})
	@DisplayName("닉네임이 없거나 공백이면 IllegalArgumentException")
	void blankNickname(String nickname) {
		assertThatThrownBy(() -> User.create(EMAIL, nickname, PROFILE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("이메일이 255자를 넘으면 IllegalArgumentException")
	void tooLongEmail() {
		String email = "a".repeat(User.EMAIL_MAX_LENGTH - "@x.com".length() + 1) + "@x.com";

		assertThatThrownBy(() -> User.create(email, NICKNAME, PROFILE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("닉네임이 50자를 넘으면 IllegalArgumentException (자르기는 가입 서비스가 한다)")
	void tooLongNickname() {
		String nickname = "가".repeat(User.NICKNAME_MAX_LENGTH + 1);

		assertThatThrownBy(() -> User.create(EMAIL, nickname, PROFILE))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("프로필 이미지 주소가 500자를 넘으면 IllegalArgumentException")
	void tooLongProfileImageUrl() {
		String profile = "a".repeat(User.PROFILE_IMAGE_URL_MAX_LENGTH + 1);

		assertThatThrownBy(() -> User.create(EMAIL, NICKNAME, profile))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("예외 메시지에 입력값(이메일)을 넣지 않는다")
	void messageHasNoInput() {
		String nickname = "가".repeat(User.NICKNAME_MAX_LENGTH + 1);

		assertThatThrownBy(() -> User.create(EMAIL, nickname, PROFILE))
			.message()
			.doesNotContain(EMAIL)
			.doesNotContain(nickname);
	}

	@Test
	@DisplayName("로그인을 기록하면 마지막 로그인 시각이 바뀌고, 다시 기록하면 새 시각으로 바뀐다")
	void recordLogin() {
		User user = User.create(EMAIL, NICKNAME, PROFILE);
		LocalDateTime first = LocalDateTime.of(2026, 10, 29, 21, 0);

		user.recordLogin(first);
		assertThat(user.getLastLoginAt()).isEqualTo(first);

		user.recordLogin(first.plusDays(1));
		assertThat(user.getLastLoginAt()).isEqualTo(first.plusDays(1));
	}

	@Test
	@DisplayName("로그인 시각이 없으면 IllegalArgumentException")
	void recordLoginWithoutTime() {
		User user = User.create(EMAIL, NICKNAME, PROFILE);

		assertThatThrownBy(() -> user.recordLogin(null))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
