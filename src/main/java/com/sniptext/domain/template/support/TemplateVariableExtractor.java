package com.sniptext.domain.template.support;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 템플릿 본문에서 변수 이름을 뽑는다. 변수 문법은 템플릿 도메인 규칙이지만 갤러리 응답에도 변수 목록이 필요하다.
 * 갤러리는 template의 entity를 쓸 수 없으므로, 엔티티가 아닌 support에 상태 없는 순수 함수로 둔다.
 * 입력은 이미 NFC로 정규화된 본문이라고 가정한다. 자모 분리형(NFD)이 그대로 오면 첫가끝 자모가
 * 가-힣·ㄱ-ㅎ·ㅏ-ㅣ 범위에 걸리지 않아 변수로 인식되지 않는다.
 */
public final class TemplateVariableExtractor {

	/** 명세 공통 규칙 5장의 변수 정규식. 형식이 틀린 중괄호는 일반 글자로 보고 건너뛴다. */
	private static final Pattern VARIABLE = Pattern.compile("\\{\\{([가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9_]+)\\}\\}");

	private TemplateVariableExtractor() {
	}

	/** 변수 이름을 첫 등장 순, 같은 이름은 한 번만 담아 수정할 수 없는 목록으로 돌려준다. null·빈 문자열이면 빈 목록이다. */
	public static List<String> extract(String content) {
		if (content == null || content.isEmpty()) {
			return List.of();
		}
		Set<String> names = new LinkedHashSet<>();
		Matcher matcher = VARIABLE.matcher(content);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return List.copyOf(names);
	}

}
