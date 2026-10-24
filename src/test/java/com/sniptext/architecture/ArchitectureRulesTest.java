package com.sniptext.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sniptext.architecture.fixture.alpha.controller.AlphaController;
import com.sniptext.architecture.fixture.alpha.repository.AlphaRepository;
import com.sniptext.architecture.fixture.alpha.service.AlphaService;
import com.sniptext.architecture.fixture.alpha.service.CleanAlphaService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/** 도메인 코드가 없는 지금도 규칙이 헛돌지 않는지 위반 예시로 확인한다. */
class ArchitectureRulesTest {

	private static final ArchitectureRules RULES = ArchitectureRules.forRoot("com.sniptext.architecture.fixture");

	private final JavaClasses fixtures = new ClassFileImporter()
		.importPackages("com.sniptext.architecture.fixture");

	@Test
	@DisplayName("컨트롤러가 레포지토리·엔티티를 쓰면 규칙이 실패한다")
	void controllerRule() {
		assertThatThrownBy(() -> RULES.controllersDoNotUseRepositoriesOrEntities().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining(AlphaController.class.getSimpleName());
	}

	@Test
	@DisplayName("서비스가 컨트롤러를 쓰면 규칙이 실패한다")
	void serviceRule() {
		assertThatThrownBy(() -> RULES.servicesDoNotUseControllers().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining(AlphaService.class.getSimpleName());
	}

	@Test
	@DisplayName("레포지토리가 서비스를 쓰면 규칙이 실패한다")
	void repositoryRule() {
		assertThatThrownBy(() -> RULES.repositoriesDoNotUseServicesOrControllers().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining(AlphaRepository.class.getSimpleName());
	}

	@Test
	@DisplayName("다른 도메인의 레포지토리를 직접 쓰면 실패하고, 같은 도메인의 레포지토리는 허용한다")
	void otherDomainRepositoryRule() {
		assertThatThrownBy(() -> RULES.noOtherDomainRepositories().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("BetaRepository");

		JavaClasses sameDomainOnly = new ClassFileImporter().importClasses(CleanAlphaService.class, AlphaRepository.class);
		assertThatCode(() -> RULES.noOtherDomainRepositories().check(sameDomainOnly)).doesNotThrowAnyException();
	}

}
