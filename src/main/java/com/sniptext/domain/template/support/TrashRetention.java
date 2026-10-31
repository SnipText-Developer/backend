package com.sniptext.domain.template.support;

import java.time.LocalDateTime;

/**
 * 휴지통 보관 규칙. 삭제하고 30일이 지나면 자동 영구 삭제 대상이 된다.
 * 목록의 예정 시각과 휴지통 정리 배치가 같은 기간을 쓰도록 여기 한 곳에 둔다.
 */
public final class TrashRetention {

	private static final int RETENTION_DAYS = 30;

	private TrashRetention() {
	}

	public static LocalDateTime purgeAt(LocalDateTime deletedAt) {
		return deletedAt.plusDays(RETENTION_DAYS);
	}

}
