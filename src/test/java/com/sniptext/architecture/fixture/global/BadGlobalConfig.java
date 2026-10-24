package com.sniptext.architecture.fixture.global;

import com.sniptext.architecture.fixture.domain.alpha.service.AlphaService;

// 위반: global이 domain을 참조한다
public class BadGlobalConfig {

	private AlphaService alphaService;

}
