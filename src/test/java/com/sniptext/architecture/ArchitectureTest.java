package com.sniptext.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 운영 코드(src/main)에 의존 규칙을 적용한다. 규칙이 깨지면 테스트가 아니라 구조를 고친다. */
@AnalyzeClasses(packages = "com.sniptext", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	private static final ArchitectureRules RULES = ArchitectureRules.forRoot("com.sniptext");

	@ArchTest
	static final ArchRule 컨트롤러는_레포지토리와_엔티티를_쓰지_않는다 = RULES.controllersDoNotUseRepositoriesOrEntities();

	@ArchTest
	static final ArchRule 서비스는_컨트롤러를_쓰지_않는다 = RULES.servicesDoNotUseControllers();

	@ArchTest
	static final ArchRule 레포지토리는_서비스와_컨트롤러를_쓰지_않는다 = RULES.repositoriesDoNotUseServicesOrControllers();

	@ArchTest
	static final ArchRule global은_domain을_참조하지_않는다 = RULES.globalDoesNotUseDomain();

	@ArchTest
	static final ArchRule 다른_도메인의_레포지토리와_엔티티를_직접_쓰지_않는다 = RULES.noOtherDomainRepositoriesOrEntities();

	@ArchTest
	static final ArchRule support는_같은_도메인의_서비스_레포지토리_컨트롤러를_쓰지_않는다 = RULES.supportDoesNotUseSameDomainLayers();

	@ArchTest
	static final ArchRule LoginUser_파라미터는_Long_타입이다 = RULES.loginUserParametersAreLong();

}
