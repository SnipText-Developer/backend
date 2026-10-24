package com.sniptext.architecture.fixture.alpha.service;

import com.sniptext.architecture.fixture.alpha.controller.AlphaController;
import com.sniptext.architecture.fixture.beta.repository.BetaRepository;

// 위반: 서비스가 컨트롤러를 쓰고, 다른 도메인의 레포지토리를 직접 부른다
public class AlphaService {

	private AlphaController alphaController;
	private BetaRepository betaRepository;

}
