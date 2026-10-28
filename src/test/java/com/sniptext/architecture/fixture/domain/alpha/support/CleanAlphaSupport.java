package com.sniptext.architecture.fixture.domain.alpha.support;

import java.util.List;

import com.sniptext.architecture.fixture.domain.alpha.entity.AlphaStatus;

// 허용: support가 자바 기본 클래스와 같은 도메인 entity 패키지의 열거형만 쓰는 순수 함수다
public final class CleanAlphaSupport {

	private CleanAlphaSupport() {
	}

	public static List<AlphaStatus> statuses() {
		return List.of(AlphaStatus.ACTIVE);
	}

}
