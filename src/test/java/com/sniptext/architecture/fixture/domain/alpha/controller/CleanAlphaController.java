package com.sniptext.architecture.fixture.domain.alpha.controller;

import com.sniptext.architecture.fixture.domain.alpha.entity.AlphaStatus;

// 허용: 컨트롤러가 같은 도메인 entity 패키지의 열거형을 쓴다
public class CleanAlphaController {

	public AlphaStatus status() {
		return AlphaStatus.ACTIVE;
	}

}
