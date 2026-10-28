package com.sniptext.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.sniptext.global.security.LoginUser;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * CLAUDE.md "패키지 구조"·"도메인 내부"의 의존 규칙과 "인증"의 @LoginUser 타입 규칙. {루트}.domain.{도메인}을 도메인으로, {루트}.global을 공통 코드로 본다.
 * 도메인 코드가 아직 없어도 실패하지 않도록 allowEmptyShould(true)를 둔다.
 */
final class ArchitectureRules {

	private static final DescribedPredicate<JavaClass> ENUM = DescribedPredicate.describe("열거형", JavaClass::isEnum);

	private final String domainRoot;
	private final String globalRoot;

	private ArchitectureRules(String root) {
		this.domainRoot = root + ".domain";
		this.globalRoot = root + ".global";
	}

	static ArchitectureRules forRoot(String root) {
		return new ArchitectureRules(root);
	}

	/**
	 * 컨트롤러는 서비스만 부른다. 레포지토리·엔티티를 직접 쓰지 않는다 (엔티티를 응답으로 내보내지 않는다).
	 * entity 패키지의 열거형은 값이므로 허용한다.
	 */
	ArchRule controllersDoNotUseRepositoriesOrEntities() {
		DescribedPredicate<JavaClass> repositoryOrEntityClass = resideInAPackage(layer("repository"))
			.or(resideInAPackage(layer("entity")).and(not(ENUM)))
			.as("repository 패키지의 클래스 또는 entity 패키지의 열거형이 아닌 클래스");
		return noClasses().that().resideInAPackage(layer("controller"))
			.should().dependOnClassesThat(repositoryOrEntityClass)
			.because("컨트롤러는 서비스만 부르고 엔티티를 응답으로 내보내지 않는다 (entity 패키지의 열거형은 허용)")
			.allowEmptyShould(true);
	}

	/** 서비스는 컨트롤러의 요청·응답 객체와 성공 코드를 모른다 (command/query/result로 주고받는다). */
	ArchRule servicesDoNotUseControllers() {
		return noClasses().that().resideInAPackage(layer("service"))
			.should().dependOnClassesThat().resideInAPackage(layer("controller"))
			.because("컨트롤러의 요청 객체를 서비스에 그대로 넘기지 않고, 성공 코드는 컨트롤러만 쓴다")
			.allowEmptyShould(true);
	}

	ArchRule repositoriesDoNotUseServicesOrControllers() {
		return noClasses().that().resideInAPackage(layer("repository"))
			.should().dependOnClassesThat().resideInAnyPackage(layer("service"), layer("controller"))
			.allowEmptyShould(true);
	}

	ArchRule globalDoesNotUseDomain() {
		return noClasses().that().resideInAPackage(globalRoot + "..")
			.should().dependOnClassesThat().resideInAPackage(domainRoot + "..")
			.because("global은 domain을 참조하지 않는다")
			.allowEmptyShould(true);
	}

	/** 다른 도메인은 그 도메인의 서비스만 부른다. 남의 레포지토리·엔티티를 직접 쓰지 않는다 (entity 패키지의 열거형은 허용). */
	ArchRule noOtherDomainRepositoriesOrEntities() {
		return classes().that().resideInAPackage(domainRoot + "..")
			.should(notDependOnOtherDomainRepositoriesOrEntities())
			.because("다른 도메인은 그 도메인의 서비스만 부른다 (entity 패키지의 열거형은 허용)")
			.allowEmptyShould(true);
	}

	/**
	 * support는 다른 도메인이 써도 되는 순수 규칙만 둔다. 같은 도메인의 service·repository·controller를 참조하면
	 * 다른 도메인이 support를 통해 그 계층에 닿게 되므로 막는다.
	 */
	ArchRule supportDoesNotUseSameDomainLayers() {
		return classes().that().resideInAPackage(layer("support"))
			.should(notDependOnSameDomainLayers("service", "repository", "controller"))
			.because("support는 상태·DB·스프링 빈 없는 순수 규칙만 둔다")
			.allowEmptyShould(true);
	}

	/**
	 * {@code @LoginUser}는 Long 파라미터에만 쓴다. 다른 타입이면 리졸버가 예외를 던지지만, 실행 전에 막는다.
	 * 리졸버가 타입을 가리던 때에는 잘못 쓴 파라미터에 쿼리 값(?userId=999)이 들어갔다.
	 */
	ArchRule loginUserParametersAreLong() {
		return methods().should(haveOnlyLongLoginUserParameters())
			.because("@LoginUser는 Long만 허용한다. 다른 타입으로 쓰면 쿼리 값이 들어가는 구멍이 생긴다")
			.allowEmptyShould(true);
	}

	private ArchCondition<JavaMethod> haveOnlyLongLoginUserParameters() {
		return new ArchCondition<>("@LoginUser 파라미터는 java.lang.Long 타입이다") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				for (JavaParameter parameter : method.getParameters()) {
					if (parameter.isAnnotatedWith(LoginUser.class) && !parameter.getRawType().isEquivalentTo(Long.class)) {
						String message = "%s의 %d번째 파라미터가 @LoginUser인데 %s 타입이다".formatted(
							method.getFullName(), parameter.getIndex() + 1, parameter.getRawType().getName());
						events.add(SimpleConditionEvent.violated(method, message));
					}
				}
			}
		};
	}

	private String layer(String name) {
		return domainRoot + ".*." + name + "..";
	}

	private ArchCondition<JavaClass> notDependOnOtherDomainRepositoriesOrEntities() {
		return new ArchCondition<>("다른 도메인의 repository·entity에 의존하지 않는다") {
			@Override
			public void check(JavaClass origin, ConditionEvents events) {
				String originDomain = domainOf(origin.getPackageName());
				for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
					JavaClass target = dependency.getTargetClass().getBaseComponentType();
					String targetPackage = target.getPackageName();
					String targetDomain = domainOf(targetPackage);
					if (targetDomain == null || targetDomain.equals(originDomain)) {
						continue;
					}
					String targetDomainPackage = domainRoot + "." + targetDomain + ".";
					boolean inRepository = (targetPackage + ".").startsWith(targetDomainPackage + "repository.");
					boolean inEntity = (targetPackage + ".").startsWith(targetDomainPackage + "entity.");
					if (inRepository || (inEntity && !target.isEnum())) {
						events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
					}
				}
			}
		};
	}

	private ArchCondition<JavaClass> notDependOnSameDomainLayers(String... layers) {
		return new ArchCondition<>("같은 도메인의 " + String.join("·", layers) + "에 의존하지 않는다") {
			@Override
			public void check(JavaClass origin, ConditionEvents events) {
				String originDomain = domainOf(origin.getPackageName());
				String originDomainPackage = domainRoot + "." + originDomain + ".";
				for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
					String targetPackage = dependency.getTargetClass().getBaseComponentType().getPackageName() + ".";
					for (String layer : layers) {
						if (targetPackage.startsWith(originDomainPackage + layer + ".")) {
							events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
						}
					}
				}
			}
		};
	}

	private String domainOf(String packageName) {
		String prefix = domainRoot + ".";
		if (!packageName.startsWith(prefix)) {
			return null;
		}
		String rest = packageName.substring(prefix.length());
		int dot = rest.indexOf('.');
		return dot < 0 ? rest : rest.substring(0, dot);
	}

}
