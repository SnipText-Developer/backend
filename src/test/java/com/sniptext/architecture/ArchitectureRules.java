package com.sniptext.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * CLAUDE.md "패키지 구조"의 의존 규칙. 루트 패키지 바로 아래 패키지를 도메인으로 본다 (com.sniptext.{도메인}).
 * 도메인 코드가 아직 없어도 실패하지 않도록 allowEmptyShould(true)를 둔다.
 */
final class ArchitectureRules {

	private final String root;

	private ArchitectureRules(String root) {
		this.root = root;
	}

	static ArchitectureRules forRoot(String root) {
		return new ArchitectureRules(root);
	}

	/** 컨트롤러는 서비스만 부른다. 레포지토리·엔티티를 직접 쓰지 않는다 (엔티티를 응답으로 내보내지 않는다). */
	ArchRule controllersDoNotUseRepositoriesOrEntities() {
		return noClasses().that().resideInAPackage(layer("controller"))
			.should().dependOnClassesThat().resideInAnyPackage(layer("repository"), layer("entity"))
			.because("컨트롤러는 서비스만 부르고 엔티티를 응답으로 내보내지 않는다")
			.allowEmptyShould(true);
	}

	/** 서비스는 컨트롤러의 요청·응답 객체를 받지 않는다 (command/query/result로 바꿔 주고받는다). */
	ArchRule servicesDoNotUseControllers() {
		return noClasses().that().resideInAPackage(layer("service"))
			.should().dependOnClassesThat().resideInAPackage(layer("controller"))
			.because("컨트롤러의 요청 객체를 서비스에 그대로 넘기지 않는다")
			.allowEmptyShould(true);
	}

	ArchRule repositoriesDoNotUseServicesOrControllers() {
		return noClasses().that().resideInAPackage(layer("repository"))
			.should().dependOnClassesThat().resideInAnyPackage(layer("service"), layer("controller"))
			.allowEmptyShould(true);
	}

	/** 다른 도메인의 레포지토리를 직접 부르지 말고 그 도메인의 서비스를 거친다. */
	ArchRule noOtherDomainRepositories() {
		return classes().that().resideInAPackage(root + "..")
			.should(notDependOnOtherDomainRepositories())
			.allowEmptyShould(true);
	}

	private String layer(String name) {
		return root + ".*." + name + "..";
	}

	private ArchCondition<JavaClass> notDependOnOtherDomainRepositories() {
		return new ArchCondition<>("다른 도메인의 repository에 의존하지 않는다") {
			@Override
			public void check(JavaClass origin, ConditionEvents events) {
				String originDomain = domainOf(origin.getPackageName());
				for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
					String targetPackage = dependency.getTargetClass().getBaseComponentType().getPackageName();
					String targetDomain = domainOf(targetPackage);
					boolean isRepository = targetDomain != null
						&& (targetPackage + ".").startsWith(root + "." + targetDomain + ".repository.");
					if (isRepository && !targetDomain.equals(originDomain)) {
						events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
					}
				}
			}
		};
	}

	private String domainOf(String packageName) {
		String prefix = root + ".";
		if (!packageName.startsWith(prefix)) {
			return null;
		}
		String rest = packageName.substring(prefix.length());
		int dot = rest.indexOf('.');
		return dot < 0 ? rest : rest.substring(0, dot);
	}

}
