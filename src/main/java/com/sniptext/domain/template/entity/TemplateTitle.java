package com.sniptext.domain.template.entity;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 템플릿 이름. NFC 정규화와 앞뒤 공백 제거를 한 뒤 1~50자인 값만 만들 수 있다. 이름 중복은 허용한다.
 * NFC는 맥 자모 분리형 입력이 글자 수를 부풀리고 화면 검색에서 어긋나지 않게 하기 위해서다.
 * 공백은 화면(JS trim)과 같은 기준으로 본다 (TextWhitespace).
 * 사용자에게 보여 줄 에러(TEMPLATE400_1·2)는 요청 단계(@ValidTemplateTitle)에서 먼저 막으므로, create에서 실패하면
 * 검사를 빠뜨린 버그다. 메시지는 로그에 남으므로 입력값 없이 위반 이름만 넣는다. 같은 이유로 toString을 만들지 않는다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateTitle {

	private static final int MAX_LENGTH = 50;

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
		return TextWhitespace.strip(Normalizer.normalize(title, Normalizer.Form.NFC));
	}

}
