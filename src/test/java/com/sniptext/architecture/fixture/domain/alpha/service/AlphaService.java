package com.sniptext.architecture.fixture.domain.alpha.service;

import com.sniptext.architecture.fixture.domain.alpha.controller.AlphaController;
import com.sniptext.architecture.fixture.domain.beta.entity.BetaEntity;
import com.sniptext.architecture.fixture.domain.beta.repository.BetaRepository;

// 위반: 서비스가 컨트롤러를 쓰고, 다른 도메인의 레포지토리·엔티티를 직접 쓴다
public class AlphaService {

	private AlphaController alphaController;
	private BetaRepository betaRepository;
	private BetaEntity betaEntity;

}
