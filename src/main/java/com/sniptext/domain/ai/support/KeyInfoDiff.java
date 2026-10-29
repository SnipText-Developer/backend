package com.sniptext.domain.ai.support;

import java.util.List;

/**
 * 핵심 정보 검사 결과. missingValues는 원문에 적힌 그대로, addedValues는 결과에 적힌 그대로 담는다.
 * 두 목록 모두 글에 처음 나온 순서이고, 수정할 수 없다.
 */
public record KeyInfoDiff(List<String> missingValues, List<String> addedValues) {

	public KeyInfoDiff {
		missingValues = List.copyOf(missingValues);
		addedValues = List.copyOf(addedValues);
	}

	public boolean hasDifference() {
		return !missingValues.isEmpty() || !addedValues.isEmpty();
	}

	/** 값은 원문 조각이라 로그에 남지 않도록 개수만 보인다. */
	@Override
	public String toString() {
		return "KeyInfoDiff[missing=" + missingValues.size() + ", added=" + addedValues.size() + "]";
	}

}
