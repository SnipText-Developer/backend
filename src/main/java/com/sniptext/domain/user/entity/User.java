package com.sniptext.domain.user.entity;

import java.time.LocalDateTime;

import com.sniptext.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원. 가입 완료 시점에만 만든다 (약관 동의 전에는 회원을 저장하지 않는다).
 * 정렬 기준은 RECENT_USED, 플랜 지정값은 없음(FREE 판정)으로 시작한다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	public static final int EMAIL_MAX_LENGTH = 255;
	public static final int NICKNAME_MAX_LENGTH = 50;
	public static final int PROFILE_IMAGE_URL_MAX_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false, length = EMAIL_MAX_LENGTH)
	private String email;

	@Column(name = "nickname", nullable = false, length = NICKNAME_MAX_LENGTH)
	private String nickname;

	@Column(name = "profile_image_url", length = PROFILE_IMAGE_URL_MAX_LENGTH)
	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "template_sort", nullable = false, length = 20)
	private TemplateSort templateSort;

	@Enumerated(EnumType.STRING)
	@Column(name = "plan_override", length = 10)
	private Plan planOverride;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Builder(access = AccessLevel.PRIVATE)
	private User(String email, String nickname, String profileImageUrl) {
		this.email = email;
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.templateSort = TemplateSort.RECENT_USED;
	}

	/**
	 * 가입할 때 회원을 만든다. 닉네임 정리(앞뒤 공백 제거, 50자 자르기, 비면 이메일 앞부분)는 가입 서비스가 끝낸 값을 받고,
	 * 여기서는 컬럼에 들어갈 수 있는지만 검사한다. 프로필 이미지는 없으면 null이다 (화면이 닉네임 첫 글자 아바타를 그린다).
	 * 여기서 실패하면 서비스가 검사를 빠뜨린 버그다. 500과 에러 로그로 드러나도록 IllegalArgumentException을 던진다.
	 * 메시지는 로그에 남으므로 입력값을 넣지 않는다.
	 */
	public static User create(String email, String nickname, String profileImageUrl) {
		require(email != null && !email.isBlank(), "이메일이 없다");
		require(email.length() <= EMAIL_MAX_LENGTH, "이메일이 255자를 넘는다");
		require(nickname != null && !nickname.isBlank(), "닉네임이 없다");
		require(nickname.length() <= NICKNAME_MAX_LENGTH, "닉네임이 50자를 넘는다");
		require(profileImageUrl == null || profileImageUrl.length() <= PROFILE_IMAGE_URL_MAX_LENGTH,
			"프로필 이미지 주소가 500자를 넘는다");
		return User.builder()
			.email(email)
			.nickname(nickname)
			.profileImageUrl(profileImageUrl)
			.build();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException("회원 생성 규칙 위반: " + message);
		}
	}

}
