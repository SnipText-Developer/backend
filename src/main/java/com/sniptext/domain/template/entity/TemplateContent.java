package com.sniptext.domain.template.entity;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

import com.sniptext.global.crypto.EncryptedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 템플릿 본문. NFC 정규화와 CRLF → LF 통일을 한 뒤 1~3000자인 값만 만들 수 있다 (글자 수는 화면과 같은 String.length()).
 * NFC는 맥 자모 분리형 입력이 글자 수를 부풀리고 화면 검색에서 어긋나지 않게 하기 위해서다.
 * 앞뒤 공백과 줄바꿈은 본문의 일부라 그대로 둔다.
 * create의 예외는 요청 검증을 빠뜨린 버그다. 로그에 남으므로 메시지와 toString에 입력값을 넣지 않는다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemplateContent {

	private static final int MAX_LENGTH = 3000;

	// DB에는 AES-GCM 암호문(v1:...)으로 저장한다. DB에서 본문을 검색하지 않는다 (검색은 화면에서)
	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String value;

	private TemplateContent(String value) {
		this.value = value;
	}

	public static TemplateContent create(String content) {
		findViolation(content).ifPresent(violation -> {
			throw new IllegalArgumentException("템플릿 본문 규칙 위반: " + violation);
		});
		return new TemplateContent(normalize(content));
	}

	/** 검사 순서(빈 값 → 길이)대로 첫 번째 위반을 돌려준다. 위반이 없으면 빈 값이다. */
	public static Optional<TemplateContentViolation> findViolation(String content) {
		if (content == null) {
			return Optional.of(TemplateContentViolation.EMPTY);
		}

		String normalized = normalize(content);
		if (TextWhitespace.isBlank(normalized)) {
			return Optional.of(TemplateContentViolation.EMPTY);
		}
		if (normalized.length() > MAX_LENGTH) {
			return Optional.of(TemplateContentViolation.TOO_LONG);
		}

		return Optional.empty();
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

	private static String normalize(String content) {
		return Normalizer.normalize(content, Normalizer.Form.NFC).replace("\r\n", "\n");
	}

}
