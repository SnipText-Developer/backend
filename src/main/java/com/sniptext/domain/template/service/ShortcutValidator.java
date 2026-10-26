package com.sniptext.domain.template.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.sniptext.domain.template.entity.ShortcutViolation;

/**
 * 단축어 규칙. 형식 검사 전에 NFC 정규화를 먼저 한다 (맥에서 입력한 자모 분리형도 같은 글자로 보기 위해서다).
 * 표시용 단축어는 정규화만 하고 대소문자는 입력 그대로, 비교 키는 정규화 + 소문자로 만든다.
 */
public final class ShortcutValidator {

	private static final String PREFIX = "/";
	private static final Pattern FORMAT = Pattern.compile("^/[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]{2,10}$");
	private static final Set<String> RESERVED_KEYS = Set.of("/톤교정");

	private ShortcutValidator() {
	}

	/** 검사 순서(빈 값 → 형식 → 예약어)대로 첫 번째 위반을 돌려준다. 위반이 없으면 빈 값이다. */
	public static Optional<ShortcutViolation> findViolation(String shortcut) {

		if (shortcut == null || shortcut.isEmpty() || shortcut.equals(PREFIX)) {
			return Optional.of(ShortcutViolation.EMPTY);
		}

		String normalized = normalize(shortcut);
		if (!FORMAT.matcher(normalized).matches()) {
			return Optional.of(ShortcutViolation.INVALID_FORMAT);
		}
		if (RESERVED_KEYS.contains(toKey(normalized))) {
			return Optional.of(ShortcutViolation.RESERVED);
		}

		return Optional.empty();
	}

	/** 표시용 단축어. NFC로 정규화하고 대소문자는 그대로 둔다. */
	public static String normalize(String shortcut) {
		return Normalizer.normalize(shortcut, Normalizer.Form.NFC);
	}

	/** 중복 비교용 키 (shortcut_key). NFC로 정규화한 뒤 소문자로 바꾼다. */
	public static String toKey(String shortcut) {
		return normalize(shortcut).toLowerCase(Locale.ROOT);
	}

}
