package com.sniptext.domain.ai.support;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sniptext.domain.ai.support.KeyInfoExtractor.Found;

/**
 * AI 톤 교정의 원문과 결과에서 단위가 붙은 숫자(금액·날짜·시각·기간·개수)를 뽑아 비교한다.
 * 원문 값은 결과에 그 값을 덮는(같거나 더 구체적인) 값이 없으면 빠진 값, 결과 값은 원문에 덮는 값이 없으면 새로 생긴 값이다.
 * 상태 없는 순수 함수다. 원문은 저장·로그 금지 대상이라 예외 메시지에도 넣지 않는다.
 */
public final class KeyInfoChecker {

	private KeyInfoChecker() {
	}

	public static KeyInfoDiff compare(String original, String corrected) {
		if (original == null || corrected == null) {
			throw new IllegalArgumentException("비교할 글은 null일 수 없다");
		}
		Map<KeyInfoValue, String> originalValues = distinct(KeyInfoExtractor.extract(original));
		Map<KeyInfoValue, String> correctedValues = distinct(KeyInfoExtractor.extract(corrected));
		return new KeyInfoDiff(
			notCovered(originalValues, correctedValues.keySet()),
			notCovered(correctedValues, originalValues.keySet()));
	}

	/** 정규형이 같은 값은 처음 나온 표기 하나만 남긴다. */
	private static Map<KeyInfoValue, String> distinct(List<Found> found) {
		Map<KeyInfoValue, String> values = new LinkedHashMap<>();
		for (Found each : found) {
			values.putIfAbsent(each.value(), each.text());
		}
		return values;
	}

	private static List<String> notCovered(Map<KeyInfoValue, String> values, Collection<KeyInfoValue> others) {
		return values.entrySet().stream()
			.filter(entry -> others.stream().noneMatch(other -> other.covers(entry.getKey())))
			.map(Map.Entry::getValue)
			.toList();
	}

}
