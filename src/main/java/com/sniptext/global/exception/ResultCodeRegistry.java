package com.sniptext.global.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import com.sniptext.global.response.ResultCode;

/**
 * 결과 코드 문자열로 열거형 값을 찾는다. 검증 어노테이션의 message에 적은 코드를 응답 코드로 바꿀 때 쓴다.
 * 기동 시 com.sniptext 아래 ResultCode 열거형을 모두 모으고, 같은 코드가 두 번 나오면 기동을 멈춘다.
 */
@Component
public class ResultCodeRegistry {

	private static final String BASE_PACKAGE = "com.sniptext";

	private final Map<String, ResultCode> resultCodes;

	public ResultCodeRegistry() {
		this.resultCodes = scan();
	}

	public Optional<ResultCode> find(String code) {
		return Optional.ofNullable(code).map(resultCodes::get);
	}

	private static Map<String, ResultCode> scan() {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
				// 상수별 본문이 있는 열거형은 abstract로 읽히므로 독립 클래스인지만 본다
				return beanDefinition.getMetadata().isIndependent();
			}
		};
		scanner.addIncludeFilter(new AssignableTypeFilter(ResultCode.class));

		Map<String, ResultCode> codes = new HashMap<>();
		for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
			Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), ClassUtils.getDefaultClassLoader());
			if (!type.isEnum()) {
				continue;
			}
			for (Object constant : type.getEnumConstants()) {
				ResultCode resultCode = (ResultCode)constant;
				ResultCode duplicate = codes.putIfAbsent(resultCode.getCode(), resultCode);
				if (duplicate != null) {
					throw new IllegalStateException("결과 코드가 중복됐습니다: %s (%s, %s)".formatted(
						resultCode.getCode(), describe(duplicate), describe(resultCode)));
				}
			}
		}
		return Map.copyOf(codes);
	}

	private static String describe(ResultCode resultCode) {
		return ((Enum<?>)resultCode).getDeclaringClass().getSimpleName() + "." + ((Enum<?>)resultCode).name();
	}

}
