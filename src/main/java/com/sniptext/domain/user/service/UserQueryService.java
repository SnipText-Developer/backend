package com.sniptext.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.domain.user.service.dto.result.UserDetailResult;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/** 회원 조회 (다른 도메인이 회원 정보를 읽을 때 쓰는 진입점). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

	private final UserRepository userRepository;

	/** 내 정보. 없거나 탈퇴한 회원이면 USER404_1. */
	public UserDetailResult getDetail(Long userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
			.map(UserDetailResult::from)
			.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
	}

}
