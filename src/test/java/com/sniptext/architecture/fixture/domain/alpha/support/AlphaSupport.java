package com.sniptext.architecture.fixture.domain.alpha.support;

import com.sniptext.architecture.fixture.domain.alpha.controller.AlphaController;
import com.sniptext.architecture.fixture.domain.alpha.repository.AlphaRepository;
import com.sniptext.architecture.fixture.domain.alpha.service.AlphaService;

// 위반: support가 같은 도메인의 서비스·레포지토리·컨트롤러를 쓴다
public class AlphaSupport {

	private AlphaService alphaService;
	private AlphaRepository alphaRepository;
	private AlphaController alphaController;

}
