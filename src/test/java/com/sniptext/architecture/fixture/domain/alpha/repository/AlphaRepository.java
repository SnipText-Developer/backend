package com.sniptext.architecture.fixture.domain.alpha.repository;

import com.sniptext.architecture.fixture.domain.alpha.service.AlphaService;

// 위반: 레포지토리가 서비스를 쓴다
public class AlphaRepository {

	private AlphaService alphaService;

}
