package com.sniptext.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sniptext.architecture.fixture.domain.alpha.controller.AlphaController;
import com.sniptext.architecture.fixture.domain.alpha.controller.CleanAlphaController;
import com.sniptext.architecture.fixture.domain.alpha.controller.CleanLoginUserController;
import com.sniptext.architecture.fixture.domain.alpha.controller.LoginUserMisuseController;
import com.sniptext.architecture.fixture.domain.alpha.entity.AlphaEntity;
import com.sniptext.architecture.fixture.domain.alpha.entity.AlphaStatus;
import com.sniptext.architecture.fixture.domain.alpha.repository.AlphaRepository;
import com.sniptext.architecture.fixture.domain.alpha.service.AlphaService;
import com.sniptext.architecture.fixture.domain.alpha.service.CleanAlphaService;
import com.sniptext.architecture.fixture.domain.alpha.support.AlphaSupport;
import com.sniptext.architecture.fixture.domain.alpha.support.CleanAlphaSupport;
import com.sniptext.architecture.fixture.domain.alpha.support.CrossDomainAlphaSupport;
import com.sniptext.architecture.fixture.domain.beta.entity.BetaCategory;
import com.sniptext.architecture.fixture.domain.beta.repository.BetaRepository;
import com.sniptext.architecture.fixture.global.BadGlobalConfig;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

/** 도메인 코드가 없는 지금도 규칙이 헛돌지 않는지 위반 예시로 확인한다. */
class ArchitectureRulesTest {

	private static final ArchitectureRules RULES = ArchitectureRules.forRoot("com.sniptext.architecture.fixture");

	private final JavaClasses fixtures = new ClassFileImporter()
		.importPackages("com.sniptext.architecture.fixture");

	@Test
	@DisplayName("컨트롤러가 레포지토리·엔티티 클래스를 쓰면 규칙이 실패한다")
	void controllerRule() {
		assertThatThrownBy(() -> RULES.controllersDoNotUseRepositoriesOrEntities().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining(AlphaController.class.getSimpleName())
			.hasMessageContaining(AlphaRepository.class.getSimpleName())
			.hasMessageContaining(AlphaEntity.class.getSimpleName())
			.hasMessageNotContaining(AlphaStatus.class.getSimpleName());
	}

	@Test
	@DisplayName("컨트롤러가 entity 패키지의 열거형을 쓰는 것은 허용한다")
	void controllerRuleAllowsEntityEnum() {
		JavaClasses enumOnly = new ClassFileImporter()
			.importClasses(CleanAlphaController.class, AlphaStatus.class);
		assertThatCode(() -> RULES.controllersDoNotUseRepositoriesOrEntities().check(enumOnly))
			.doesNotThrowAnyException();
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
	@DisplayName("global이 domain을 참조하면 규칙이 실패한다")
	void globalRule() {
		assertThatThrownBy(() -> RULES.globalDoesNotUseDomain().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining(BadGlobalConfig.class.getSimpleName());
	}

	@Test
	@DisplayName("다른 도메인의 레포지토리·엔티티 클래스를 직접 쓰면 실패하고, 같은 도메인의 것과 다른 도메인 entity 패키지의 열거형은 허용한다")
	void otherDomainRule() {
		assertThatThrownBy(() -> RULES.noOtherDomainRepositoriesOrEntities().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining("BetaRepository")
			.hasMessageContaining("BetaEntity")
			.hasMessageNotContaining(BetaCategory.class.getSimpleName());

		JavaClasses allowedOnly = new ClassFileImporter()
			.importClasses(CleanAlphaService.class, AlphaRepository.class, AlphaEntity.class, BetaCategory.class);
		assertThatCode(() -> RULES.noOtherDomainRepositoriesOrEntities().check(allowedOnly))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("support가 같은 도메인이든 다른 도메인이든 서비스·레포지토리·컨트롤러를 쓰면 실패하고, entity 패키지의 열거형만 쓰면 허용한다")
	void supportRule() {
		assertThatThrownBy(() -> RULES.supportHoldsOnlyPureRules().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining(AlphaSupport.class.getSimpleName() + ".alphaService")
			.hasMessageContaining(AlphaSupport.class.getSimpleName() + ".alphaRepository")
			.hasMessageContaining(AlphaSupport.class.getSimpleName() + ".alphaController")
			.hasMessageContaining(CrossDomainAlphaSupport.class.getSimpleName() + ".betaRepository")
			.hasMessageNotContaining(CleanAlphaSupport.class.getSimpleName());

		JavaClasses crossDomainOnly = new ClassFileImporter()
			.importClasses(CrossDomainAlphaSupport.class, BetaRepository.class);
		assertThatThrownBy(() -> RULES.supportHoldsOnlyPureRules().check(crossDomainOnly))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining(CrossDomainAlphaSupport.class.getSimpleName() + ".betaRepository");

		JavaClasses cleanOnly = new ClassFileImporter().importClasses(CleanAlphaSupport.class, AlphaStatus.class);
		assertThatCode(() -> RULES.supportHoldsOnlyPureRules().check(cleanOnly))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("@LoginUser를 long·Integer에 쓰면 실패하고, Long에 쓰면 허용한다")
	void loginUserTypeRule() {
		assertThatThrownBy(() -> RULES.loginUserParametersAreLong().check(fixtures))
			.isInstanceOf(AssertionError.class)
			.hasMessageContaining(LoginUserMisuseController.class.getSimpleName() + ".primitiveLong(long)")
			.hasMessageContaining(LoginUserMisuseController.class.getSimpleName() + ".boxedInteger(java.lang.String, java.lang.Integer)")
			.hasMessageNotContaining(CleanLoginUserController.class.getSimpleName());

		JavaClasses cleanOnly = new ClassFileImporter().importClasses(CleanLoginUserController.class);
		assertThatCode(() -> RULES.loginUserParametersAreLong().check(cleanOnly))
			.doesNotThrowAnyException();
	}

}
