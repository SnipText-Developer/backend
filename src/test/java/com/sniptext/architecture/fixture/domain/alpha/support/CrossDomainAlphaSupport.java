package com.sniptext.architecture.fixture.domain.alpha.support;

import com.sniptext.architecture.fixture.domain.beta.repository.BetaRepository;

// 위반: support가 다른 도메인의 레포지토리를 쓴다
public class CrossDomainAlphaSupport {

	private BetaRepository betaRepository;

}
