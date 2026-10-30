package com.sniptext.global.security;

/**
 * 가입용 토큰에 담는 구글 계정 정보. 약관 동의 전에는 회원을 저장하지 않으므로, 동의 API까지 이 토큰으로 넘긴다.
 * providerUserId는 ID 토큰의 sub(구글 계정 고유값)이다. 이름·프로필 이미지는 구글이 주지 않으면 null이다.
 */
public record SignupTokenClaims(String providerUserId, String email, String name, String profileImageUrl) {
}
