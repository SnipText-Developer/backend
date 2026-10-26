package com.sniptext.domain.template.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 템플릿 이름. 앞뒤 공백을 제거한 뒤 1~50자인 값만 만들 수 있다. 이름 중복은 허용한다.
 * 사용자에게 보여 줄 에러(TEMPLATE400_1·2)는 요청 단계에서 먼저 막으므로, 여기서 실패하면 검사를 빠뜨린 버그다.
 * 메시지는 로그에 남으므로 입력값을 넣지 않는다. 같은 이유로 toString을 만들지 않는다.
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
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("템플릿 이름이 비었다");
		}
		String stripped = title.strip();
		if (stripped.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("템플릿 이름이 50자를 넘는다");
		}
		return new TemplateTitle(stripped);
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

}
