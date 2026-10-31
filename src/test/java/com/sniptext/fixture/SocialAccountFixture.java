package com.sniptext.fixture;

import com.sniptext.domain.auth.entity.SocialAccount;
import com.sniptext.domain.auth.entity.SocialProvider;

/** 테스트용 소셜 계정 연결. 생성 인자가 바뀌면 여기만 고친다. */
public final class SocialAccountFixture {

	public static final String GOOGLE_SUB = "109876543210987654321";

	private SocialAccountFixture() {
	}

	public static SocialAccount google(Long userId) {
		return google(userId, GOOGLE_SUB);
	}

	public static SocialAccount google(Long userId, String googleSub) {
		return SocialAccount.create(userId, SocialProvider.GOOGLE, googleSub);
	}

}
