package com.sniptext.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/**
	 * 탈퇴하지 않은 회원인지 확인한다. 없거나 탈퇴했으면 USER404_1.
	 * 회원 행을 외래 키로 쓰는 저장(예: AI 사용량 선점) 전에 불러, 없는 회원이 외래 키 위반(500)으로 끝나지 않게 한다.
	 */
	public void validateActive(Long userId) {
		if (userRepository.findByIdAndDeletedAtIsNull(userId).isEmpty()) {
			throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
		}
	}

}
