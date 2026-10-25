package com.sniptext.domain.user.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.entity.User;
import com.sniptext.domain.user.exception.UserErrorCode;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

	private final UserRepository userRepository;

	/**
	 * 회원의 플랜 지정값(plan_override). 지정값이 없으면 빈 값이다.
	 * 현재 플랜 판정은 plan 도메인의 PlanQueryService가 하므로 여기서 기본값을 정하지 않는다.
	 */
	public Optional<Plan> findPlanOverride(Long userId) {
		User user = userRepository.findByIdAndDeletedAtIsNull(userId)
			.orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
		return Optional.ofNullable(user.getPlanOverride());
	}

}
