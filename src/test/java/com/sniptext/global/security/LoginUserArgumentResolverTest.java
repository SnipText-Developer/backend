package com.sniptext.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;

/**
 * 잘못 쓴 @LoginUser가 기본 처리(@RequestParam)로 넘어가지 않는지 확인한다.
 * 잘못 쓴 예를 테스트용 경로로 두지 않고 여기서 메서드 파라미터로만 만든다.
 */
class LoginUserArgumentResolverTest {

	private final LoginUserArgumentResolver resolver = new LoginUserArgumentResolver();

	@ParameterizedTest
	@ValueSource(strings = {"boxedLong", "primitiveLong", "boxedInteger"})
	@DisplayName("@LoginUser가 붙어 있으면 타입과 상관없이 리졸버가 맡는다")
	void supportsAnnotatedParameterOfAnyType(String methodName) {
		assertThat(resolver.supportsParameter(parameterOf(methodName))).isTrue();
	}

	@Test
	@DisplayName("@LoginUser가 없으면 Long이어도 맡지 않는다")
	void doesNotSupportWithoutAnnotation() {
		assertThat(resolver.supportsParameter(parameterOf("notAnnotated"))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"primitiveLong", "boxedInteger"})
	@DisplayName("@LoginUser 파라미터가 Long이 아니면 인증 정보를 보기 전에 IllegalStateException")
	void rejectsNonLongParameter(String methodName) {
		assertThatThrownBy(() -> resolver.resolveArgument(parameterOf(methodName), null, null, null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("@LoginUser는 Long 타입에만 쓸 수 있다");
	}

	private static MethodParameter parameterOf(String methodName) {
		for (Method method : Handlers.class.getDeclaredMethods()) {
			if (method.getName().equals(methodName)) {
				return new MethodParameter(method, 0);
			}
		}
		throw new IllegalArgumentException(methodName);
	}

	@SuppressWarnings("unused")
	private static class Handlers {

		void boxedLong(@LoginUser Long userId) {
		}

		void primitiveLong(@LoginUser long userId) {
		}

		void boxedInteger(@LoginUser Integer userId) {
		}

		void notAnnotated(Long userId) {
		}

	}

}
