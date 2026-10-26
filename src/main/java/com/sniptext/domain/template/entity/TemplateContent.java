package com.sniptext.domain.template.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 템플릿 본문. CRLF를 LF로 바꾼 뒤 1~3000자인 값만 만들 수 있다 (글자 수는 화면과 같은 String.length()).
 * 앞뒤 공백과 줄바꿈은 본문의 일부라 그대로 둔다. 형식이 틀린 중괄호도 일반 글자로 두고 막지 않는다.
 * 사용자에게 보여 줄 에러(TEMPLATE400_6·7)는 요청 단계에서 먼저 막으므로, 여기서 실패하면 검사를 빠뜨린 버그다.
 * 본문은 로그에 남기지 않으므로 메시지에 입력값을 넣지 않고 toString을 만들지 않는다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateContent {

	private static final int MAX_LENGTH = 3000;

	// TODO 본문 암호화 변환기 이슈에서 이 필드에 @Convert를 붙인다. 지금은 평문으로 저장한다
	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String value;

	private TemplateContent(String value) {
		this.value = value;
	}

	public static TemplateContent create(String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("템플릿 본문이 비었다");
		}
		String normalized = content.replace("\r\n", "\n");
		if (normalized.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("템플릿 본문이 3000자를 넘는다");
		}
		return new TemplateContent(normalized);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TemplateContent other)) {
			return false;
		}
		return Objects.equals(value, other.value);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(value);
	}

}
