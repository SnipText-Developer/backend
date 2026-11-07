package com.sniptext.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/** 회원 조회 (다른 도메인이 회원 정보를 읽을 때 쓰는 진입점). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

	private final UserRepository userRepository;

	/** 로그인·가입 응답에 담을 닉네임. 없거나 탈퇴한 회원이면 USER404_1. */
	public String getNickname(Long userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
			.map(User::getNickname)
			.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
	}

}
