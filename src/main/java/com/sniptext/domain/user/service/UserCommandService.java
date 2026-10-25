package com.sniptext.domain.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserCommandService {

	private final UserRepository userRepository;

	/**
	 * 회원 행을 비관적 쓰기 잠금으로 잡는다. 템플릿 생성·복구·갤러리 저장의 한도 검사 전에 부른다.
	 * 자기 트랜잭션을 열면 메서드가 끝날 때 잠금이 풀려 이어지는 개수 검사·저장이 잠금 없이 실행되므로,
	 * 호출자 트랜잭션 안에서만 동작하게 강제한다 (밖에서 부르면 IllegalTransactionStateException).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockUser(Long userId) {
		userRepository.findByIdAndDeletedAtIsNullForUpdate(userId)
			.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
	}

}
