package com.sniptext.fixture;

import java.time.LocalDateTime;

import org.springframework.test.util.ReflectionTestUtils;

import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.entity.User;

/**
 * 테스트용 회원. 생성 인자가 바뀌면 여기만 고친다.
 * 플랜 지정값과 탈퇴 시각은 아직 바꾸는 기능(결제·탈퇴)이 없어서 필드를 직접 넣는다.
 * 그 기능이 생기면 엔티티 메서드로 바꾼다.
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

	/** plan_override를 지정한 회원. 결제 도입 전 테스트 계정을 PRO로 만드는 것과 같다 */
	public static User userWithPlanOverride(String email, Plan planOverride) {
		User user = user(email);
		ReflectionTestUtils.setField(user, "planOverride", planOverride);
		return user;
	}

	public static User deletedUser(String email, LocalDateTime deletedAt) {
		return deletedUser(email, null, deletedAt);
	}

	/** 플랜 지정값이 있는 탈퇴 회원. PRO여도 탈퇴했으면 회원을 찾지 못하는지 볼 때 쓴다 */
	public static User deletedUser(String email, Plan planOverride, LocalDateTime deletedAt) {
		User user = userWithPlanOverride(email, planOverride);
		ReflectionTestUtils.setField(user, "deletedAt", deletedAt);
		return user;
	}

}
