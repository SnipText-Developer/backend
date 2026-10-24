package com.sniptext.architecture.fixture.alpha.controller;

import com.sniptext.architecture.fixture.alpha.entity.AlphaEntity;
import com.sniptext.architecture.fixture.alpha.repository.AlphaRepository;

// 위반: 컨트롤러가 레포지토리를 부르고 엔티티를 내보낸다
public class AlphaController {

	private AlphaRepository alphaRepository;

	public AlphaEntity find() {
		return null;
	}

}
