package com.sniptext.global.client.google;

/**
 * 검증을 통과한 구글 ID 토큰의 계정 정보. subject는 구글 계정 고유값(sub)으로 소셜 계정 연결에 쓴다.
 * 이름·프로필 이미지는 구글이 주지 않으면 null이다.
 */
public record GoogleAccount(String subject, String email, String name, String pictureUrl) {
}
