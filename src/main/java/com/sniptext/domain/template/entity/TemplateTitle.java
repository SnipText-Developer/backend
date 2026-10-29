package com.sniptext.domain.template.entity;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.sniptext.global.text.TextWhitespace;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 템플릿 이름. NFC 정규화, 가운데 줄바꿈·탭을 공백 하나로 바꾸기, 앞뒤 공백 제거를 한 뒤 1~50자인 값만 만들 수 있다.
 * NFC는 맥 자모 분리형 입력이 글자 수를 부풀리고 화면 검색에서 어긋나지 않게 하기 위해서다.
 * 줄바꿈·탭은 화면 입력칸으로는 들어오지 않지만, API로 들어오면 카드·확장 팝업처럼 한 줄로 보여 주는 곳이 깨진다.
 * create의 예외는 요청 검증을 빠뜨린 버그다. 로그에 남으므로 메시지와 toString에 입력값을 넣지 않는다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateTitle {

	private static final int MAX_LENGTH = 50;
	private static final Pattern LINE_BREAKS_AND_TABS = Pattern.compile("[\\t\\n\\x0B\\f\\r\\u0085\\u2028\\u2029]+");

	@Column(name = "title", nullable = false, length = 50)
	private String value;

	private TemplateTitle(String value) {
		this.value = value;
	}

	public static TemplateTitle create(String title) {
		findViolation(title).ifPresent(violation -> {
			throw new IllegalArgumentException("템플릿 이름 규칙 위반: " + violation);
		});
		return new TemplateTitle(normalize(title));
	}

	/** 검사 순서(빈 값 → 길이)대로 첫 번째 위반을 돌려준다. 위반이 없으면 빈 값이다. */
	public static Optional<TemplateTitleViolation> findViolation(String title) {
		if (title == null) {
			return Optional.of(TemplateTitleViolation.EMPTY);
		}

		String normalized = normalize(title);
		if (normalized.isEmpty()) {
			return Optional.of(TemplateTitleViolation.EMPTY);
		}
		if (normalized.length() > MAX_LENGTH) {
			return Optional.of(TemplateTitleViolation.TOO_LONG);
		}

		return Optional.empty();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TemplateTitle other)) {
			return false;
		}
		return Objects.equals(value, other.value);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(value);
	}

	private static String normalize(String title) {
		String nfc = Normalizer.normalize(title, Normalizer.Form.NFC);
		return TextWhitespace.strip(LINE_BREAKS_AND_TABS.matcher(nfc).replaceAll(" "));
	}

}
