package com.sniptext.domain.ai.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.ai.exception.AiErrorCode;
import com.sniptext.domain.ai.repository.AiToneDailyUsageRepository;
import com.sniptext.domain.ai.service.dto.result.AiToneReservation;
import com.sniptext.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * AI 톤 교정 하루 횟수의 선점과 반환. 요청 시 1회를 먼저 잡고, AI 오류·타임아웃·혐오 표현 차단이면 되돌린다.
 * 선점·반환은 각각 자기 트랜잭션으로 짧게 끝낸다. 톤 교정 API가 외부 AI를 부르는 동안(최대 20초)
 * DB 트랜잭션과 커넥션을 잡고 있지 않게 하기 위해서다. 그래서 호출자 트랜잭션 밖에서 부르는 것을 전제로 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiToneUsageCommandService {

	private final AiToneDailyUsageRepository aiToneDailyUsageRepository;
	private final Clock clock;

	/**
	 * 오늘(KST) 횟수를 한도 안에서 1 올려 잡는다. 이미 한도면 AI429_1.
	 * 한도는 부르는 쪽이 현재 플랜 조회로 얻어 넘긴다 (회원 존재·플랜 검사도 부르는 쪽에서 먼저 한다).
	 */
	@Transactional
	public AiToneReservation reserve(Long userId, int limit) {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate usageDate = now.toLocalDate();

		aiToneDailyUsageRepository.insertIfAbsent(userId, usageDate, now);
		if (aiToneDailyUsageRepository.incrementIfBelow(userId, usageDate, limit, now) == 0) {
			throw new BusinessException(AiErrorCode.DAILY_LIMIT_EXCEEDED);
		}
		int usedCountAfter = aiToneDailyUsageRepository.findUsedCount(userId, usageDate)
			.orElseThrow(() -> new IllegalStateException("선점한 사용량 행이 없다"));
		return new AiToneReservation(userId, usageDate, usedCountAfter);
	}

	/** 선점한 1회를 되돌린다. 지금 날짜가 아니라 선점한 날짜 행에서 뺀다. 0 아래로는 내려가지 않는다. */
	@Transactional
	public void release(AiToneReservation reservation) {
		aiToneDailyUsageRepository.decrementIfPositive(
			reservation.userId(), reservation.usageDate(), LocalDateTime.now(clock));
	}

}
