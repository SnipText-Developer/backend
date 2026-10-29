package com.sniptext.domain.template.entity;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 호출 단축어. 규칙을 통과한 값만 만들 수 있고, 표시용 값과 비교 키를 항상 같이 갖는다.
 * 형식 검사 전에 NFC 정규화를 먼저 한다 (맥에서 입력한 자모 분리형도 같은 글자로 보기 위해서다).
 * 표시용 값은 정규화만 하고 대소문자는 입력 그대로, 비교 키는 정규화 + 소문자로 만든다.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shortcut {

	private static final String PREFIX = "/";
	private static final Pattern FORMAT = Pattern.compile("^/[가-힣ㄱ-ㅎㅏ-ㅣA-Za-z0-9]{2,10}$");
	private static final Set<String> RESERVED_KEYS = Set.of("/톤교정");
	/** "/" 뒤 최대 글자 수. FORMAT의 {2,10}과 같다 */
	private static final int MAX_BODY_LENGTH = 10;
	/** 겹칠 때 붙이는 번호 범위 */
	private static final int MIN_NUMBER = 2;
	private static final int MAX_NUMBER = 99;

	/** 표시용 단축어. NFC 정규화, 대소문자는 입력 그대로 (/Quote) */
	@Column(name = "shortcut", nullable = false, length = 11)
	private String value;

	/** 중복 비교용 키. NFC 정규화 + 소문자 (/quote) */
	@Column(name = "shortcut_key", nullable = false, length = 11)
	private String key;

	private Shortcut(String normalized) {
		this.value = normalized;
		this.key = toKey(normalized);
	}

	/**
	 * 규칙을 통과한 단축어를 만든다. 사용자에게 보여 줄 에러(TEMPLATE400_3~5)는 요청 단계에서 먼저 막으므로,
	 * 여기서 실패하면 검사를 빠뜨린 버그다. 500과 에러 로그로 드러나도록 IllegalArgumentException을 던진다.
	 * 메시지는 로그에 남으므로 입력값 없이 위반 이름만 넣는다.
	 */
	public static Shortcut create(String shortcut) {
		findViolation(shortcut).ifPresent(violation -> {
			throw new IllegalArgumentException("단축어 규칙 위반: " + violation);
		});
		return new Shortcut(normalize(shortcut));
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

	/**
	 * 휴지통 복구·갤러리 저장에서 쓸 단축어를 고른다. usedKeys는 candidateKeys로 조회한, 이미 쓰는 비교 키다 (휴지통 제외).
	 * 원본 키가 비어 있으면 원본을, 겹치면 번호 후보 중 비어 있는 첫 번째를 돌려준다.
	 * 2~99가 모두 차면 빈 값이다. TEMPLATE409_2로 바꾸는 건 서비스가 한다.
	 */
	public Optional<Shortcut> findAvailable(Set<String> usedKeys) {
		if (!usedKeys.contains(key)) {
			return Optional.of(this);
		}
		return numberedCandidates().stream()
			.filter(candidate -> !usedKeys.contains(candidate.key))
			.findFirst();
	}

	/**
	 * 이 단축어와 겹칠 수 있는 비교 키를 중복 없이 돌려준다. 원본 키가 먼저, 그다음 번호 후보 키가 번호 순서대로 온다.
	 * 서비스는 이 키로 이미 쓰는 키(휴지통 제외)를 한 번에 조회해 findAvailable에 넘긴다.
	 */
	public List<String> candidateKeys() {
		return Stream.concat(Stream.of(this), numberedCandidates().stream())
			.map(Shortcut::getKey)
			.distinct()
			.toList();
	}

	/**
	 * 원본 뒤에 2~99를 붙인 후보를 번호 순서대로 돌려준다.
	 * "/" 뒤가 10자를 넘으면 원본을 뒤에서 잘라 맞추고, 숫자로 끝나는 원본도 그대로 뒤에 붙인다 (/견적2 → /견적22).
	 * "/" 뒤 9번째 글자가 숫자인 원본은 잘라 붙인 후보가 겹칠 수 있다. 같은 값을 다시 검사할 뿐이라 고르는 결과는 그대로다.
	 * - 9자 원본: 12~19번이 2~9번과 같다 (/견적요청서작성안1 + 2 = /견적요청서작성안 + 12). 서로 다른 후보는 90개
	 * - 10자 원본: 잘라 붙인 후보가 원본과 같아질 수 있다 (/견적요청서작성안내2 + 2). 원본 키는 이미 쓰는 키라 건너뛴다
	 */
	private List<Shortcut> numberedCandidates() {
		String body = value.substring(PREFIX.length());
		return IntStream.rangeClosed(MIN_NUMBER, MAX_NUMBER)
			.mapToObj(String::valueOf)
			.map(number -> {
				int bodyLength = Math.min(body.length(), MAX_BODY_LENGTH - number.length());
				return create(PREFIX + body.substring(0, bodyLength) + number);
			})
			.toList();
	}

	/**
	 * 표시용 값으로 비교한다. 비교 키는 표시용 값에서 계산되므로 따로 보지 않는다.
	 * /Quote와 /quote는 키가 같아 중복이지만 다른 값이다 (중복 판정은 키로 따로 한다).
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Shortcut other)) {
			return false;
		}
		return Objects.equals(value, other.value);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(value);
	}

	private static String normalize(String shortcut) {
		return Normalizer.normalize(shortcut, Normalizer.Form.NFC);
	}

	private static String toKey(String normalized) {
		return normalized.toLowerCase(Locale.ROOT);
	}

}
