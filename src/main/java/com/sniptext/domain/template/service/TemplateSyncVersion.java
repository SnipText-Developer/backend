package com.sniptext.domain.template.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.sniptext.global.config.TimeConfig;

/**
 * 확장 동기화 버전 (S2 명세 27번). "v1.{휴지통 제외 개수}.{가장 늦은 updated_at의 에폭 마이크로초}".
 * 시각은 DB에 저장된 한국 시간을 Asia/Seoul 기준 순간으로 바꿔 DATETIME(6)과 같은 마이크로초까지 쓴다.
 * 화면은 값을 해석하지 않고 그대로 돌려보내고, 서버도 해석하지 않고 지금 버전과 문자열이 같은지만 본다.
 * 그래서 형식이 틀린 값을 따로 거르지 않아도 "다름"이 되어 전체 목록이 나간다.
 * <p>
 * 개수는 영구 삭제(17·18번, 30일 정리 배치)로 가장 늦은 시각이 뒤로 갈 때 예전 버전과 겹치지 않게 막는다.
 * 예: A(t1)·B(t2) → 확장이 v1.2.{t2} 보관 → A 휴지통 이동(t3) → A 영구 삭제 → 최댓값이 다시 t2. 개수가 없으면 버전이 예전과 같아
 * A가 빠진 걸 확장이 모른다. 목록이 바뀌었는데 최댓값이 그대로인 경우는 바뀐 행 없이 빠지기만 한 경우뿐이고, 그때는 개수가 반드시 준다.
 * 버전 형식을 바꿀 때 개수를 빼지 않는다 (TemplateSyncControllerTest가 이 경우를 확인한다).
 */
final class TemplateSyncVersion {

	private static final String PREFIX = "v1.";
	private static final String SEPARATOR = ".";

	private TemplateSyncVersion() {
	}

	/**
	 * 템플릿이 하나도 없으면(휴지통 포함 0행) 가장 늦은 시각도 없어 "v1.0.0"이 된다.
	 * 명세에 정해져 있지 않아 개수 0 + 시각 0으로 정했다. 템플릿이 한 번이라도 저장되면 시각이 0보다 커서 겹치지 않는다
	 */
	static String of(long activeCount, LocalDateTime latestUpdatedAt) {
		return PREFIX + activeCount + SEPARATOR + toEpochMicros(latestUpdatedAt);
	}

	private static long toEpochMicros(LocalDateTime dateTime) {
		if (dateTime == null) {
			return 0L;
		}
		return ChronoUnit.MICROS.between(Instant.EPOCH, dateTime.atZone(TimeConfig.KST).toInstant());
	}

}
