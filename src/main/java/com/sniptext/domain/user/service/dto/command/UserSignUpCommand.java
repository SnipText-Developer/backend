package com.sniptext.domain.user.service.dto.command;

/** 가입 시 회원 생성 입력. 구글 계정 정보를 다듬지 않은 그대로 받는다 (이름·프로필 이미지는 없으면 null). */
public record UserSignUpCommand(String email, String name, String profileImageUrl) {
}
