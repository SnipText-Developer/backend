package com.sniptext.architecture.fixture.domain.alpha.service;

import com.sniptext.architecture.fixture.domain.alpha.entity.AlphaEntity;
import com.sniptext.architecture.fixture.domain.alpha.repository.AlphaRepository;
import com.sniptext.architecture.fixture.domain.beta.entity.BetaCategory;

// 허용: 같은 도메인의 레포지토리·엔티티와 다른 도메인 entity 패키지의 열거형을 쓴다
public class CleanAlphaService {

	private AlphaRepository alphaRepository;
	private AlphaEntity alphaEntity;

	public BetaCategory category() {
		return BetaCategory.GENERAL;
	}

}
