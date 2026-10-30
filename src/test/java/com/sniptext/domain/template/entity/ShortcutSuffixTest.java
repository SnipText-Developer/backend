package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShortcutSuffixTest {

	/** 원본 키와 원본 키 뒤에 from~to 번호를 붙인 키 */
	private static Set<String> usedWithNumbers(String originalKey, int from, int to) {
		Set<String> used = new HashSet<>();
		used.add(originalKey);
		IntStream.rangeClosed(from, to).forEach(number -> used.add(originalKey + number));
		return used;
	}

	@Test
	@DisplayName("이미 쓰는 키가 없으면 원본을 그대로 쓴다")
	void noUsedKeys() {
		Shortcut original = Shortcut.create("/견적");

		assertThat(original.findAvailable(Set.of())).contains(original);
	}

	@Test
	@DisplayName("원본 키와 겹치지 않으면 다른 키가 있어도 원본을 그대로 쓴다")
	void notConflicted() {
		Shortcut original = Shortcut.create("/견적");

		assertThat(original.findAvailable(Set.of("/견적2", "/회의"))).contains(original);
	}

	@Test
	@DisplayName("원본 키와 겹치면 뒤에 2를 붙인다")
	void conflicted() {
		Optional<Shortcut> available = Shortcut.create("/견적").findAvailable(Set.of("/견적"));

		assertThat(available).contains(Shortcut.create("/견적2"));
	}

	@Test
	@DisplayName("비어 있는 첫 번호를 쓴다")
	void firstEmptyNumber() {
		Optional<Shortcut> available = Shortcut.create("/견적").findAvailable(Set.of("/견적", "/견적2", "/견적4"));

		assertThat(available).contains(Shortcut.create("/견적3"));
	}

	@Test
	@DisplayName("2~9가 모두 쓰이면 두 자리 번호 10을 쓴다")
	void oneDigitToTwoDigits() {
		Optional<Shortcut> available = Shortcut.create("/견적").findAvailable(usedWithNumbers("/견적", 2, 9));

		assertThat(available).contains(Shortcut.create("/견적10"));
	}

	@Test
	@DisplayName("2~98이 모두 쓰이면 마지막 번호 99를 쓴다")
	void lastNumber() {
		Optional<Shortcut> available = Shortcut.create("/견적").findAvailable(usedWithNumbers("/견적", 2, 98));

		assertThat(available).contains(Shortcut.create("/견적99"));
	}

	@Test
	@DisplayName("2~99가 모두 쓰이면 빈 값을 돌려준다")
	void allNumbersUsed() {
		Optional<Shortcut> available = Shortcut.create("/견적").findAvailable(usedWithNumbers("/견적", 2, 99));

		assertThat(available).isEmpty();
	}

	@Test
	@DisplayName("비교는 키로 하고, 표시는 원본 대소문자를 유지한다 (/Quote가 /quote와 겹치면 /Quote2)")
	void keepsOriginalCase() {
		Optional<Shortcut> available = Shortcut.create("/Quote").findAvailable(Set.of("/quote"));

		assertThat(available).hasValueSatisfying(shortcut -> {
			assertThat(shortcut.getValue()).isEqualTo("/Quote2");
			assertThat(shortcut.getKey()).isEqualTo("/quote2");
		});
	}

	@Test
	@DisplayName("번호 후보도 키로 비교한다 (/quote2가 쓰이면 /Quote3)")
	void candidatesComparedByKey() {
		Optional<Shortcut> available = Shortcut.create("/Quote").findAvailable(Set.of("/quote", "/quote2"));

		assertThat(available).contains(Shortcut.create("/Quote3"));
	}

	@Test
	@DisplayName("숫자로 끝나는 원본도 뒤에 붙인다 (/견적2 → /견적22)")
	void originalEndingWithNumber() {
		Optional<Shortcut> available = Shortcut.create("/견적2").findAvailable(Set.of("/견적2"));

		assertThat(available).contains(Shortcut.create("/견적22"));
	}

	@Test
	@DisplayName("8자 원본은 두 자리 번호를 붙여도 10자라 자르지 않는다")
	void eightCharsWithTwoDigits() {
		Optional<Shortcut> available = Shortcut.create("/견적요청서작성안")
			.findAvailable(usedWithNumbers("/견적요청서작성안", 2, 9));

		assertThat(available).contains(Shortcut.create("/견적요청서작성안10"));
	}

	@Test
	@DisplayName("9자 원본은 한 자리 번호면 자르지 않는다")
	void nineCharsWithOneDigit() {
		Optional<Shortcut> available = Shortcut.create("/견적요청서작성안내").findAvailable(Set.of("/견적요청서작성안내"));

		assertThat(available).contains(Shortcut.create("/견적요청서작성안내2"));
	}

	@Test
	@DisplayName("9자 원본은 두 자리 번호면 뒤에서 1자를 자른다")
	void nineCharsWithTwoDigits() {
		Optional<Shortcut> available = Shortcut.create("/견적요청서작성안내")
			.findAvailable(usedWithNumbers("/견적요청서작성안내", 2, 9));

		assertThat(available).contains(Shortcut.create("/견적요청서작성안10"));
	}

	@Test
	@DisplayName("10자 원본은 한 자리 번호면 뒤에서 1자를 자른다 (/견적요청서작성안내문 → /견적요청서작성안내2)")
	void tenCharsWithOneDigit() {
		Optional<Shortcut> available = Shortcut.create("/견적요청서작성안내문").findAvailable(Set.of("/견적요청서작성안내문"));

		assertThat(available).contains(Shortcut.create("/견적요청서작성안내2"));
	}

	@Test
	@DisplayName("10자 원본은 두 자리 번호면 뒤에서 2자를 자른다 (/견적요청서작성안10)")
	void tenCharsWithTwoDigits() {
		Set<String> used = new HashSet<>(Set.of("/견적요청서작성안내문"));
		IntStream.rangeClosed(2, 9).forEach(number -> used.add("/견적요청서작성안내" + number));

		Optional<Shortcut> available = Shortcut.create("/견적요청서작성안내문").findAvailable(used);

		assertThat(available).contains(Shortcut.create("/견적요청서작성안10"));
	}

	@Test
	@DisplayName("숫자로 끝나는 10자 원본은 잘라 붙인 후보가 원본과 같아지면 건너뛴다 (/견적요청서작성안내2 → /견적요청서작성안내3)")
	void tenCharsEndingWithNumber() {
		Optional<Shortcut> available = Shortcut.create("/견적요청서작성안내2").findAvailable(Set.of("/견적요청서작성안내2"));

		assertThat(available).contains(Shortcut.create("/견적요청서작성안내3"));
	}

	@Test
	@DisplayName("숫자로 끝나는 9자 원본은 12~19번 후보가 2~9번과 같아 건너뛴다 (2~11 사용 중 → 20)")
	void nineCharsEndingWithNumber() {
		Set<String> used = usedWithNumbers("/견적요청서작성안1", 2, 9);
		used.add("/견적요청서작성안10");
		used.add("/견적요청서작성안11");

		Optional<Shortcut> available = Shortcut.create("/견적요청서작성안1").findAvailable(used);

		assertThat(available).contains(Shortcut.create("/견적요청서작성안20"));
	}

	@Test
	@DisplayName("숫자로 끝나는 9자 원본은 서로 다른 후보 90개가 모두 차면 빈 값을 돌려준다")
	void nineCharsEndingWithNumberAllUsed() {
		Shortcut original = Shortcut.create("/견적요청서작성안1");

		assertThat(original.findAvailable(Set.copyOf(original.candidateKeys()))).isEmpty();
	}

	@Test
	@DisplayName("후보 키는 원본 키 다음에 2~99를 붙인 키를 번호 순서대로 담는다")
	void candidateKeys() {
		List<String> keys = Shortcut.create("/Quote").candidateKeys();

		assertThat(keys).hasSize(99);
		assertThat(keys).startsWith("/quote", "/quote2", "/quote3");
		assertThat(keys.get(9)).isEqualTo("/quote10");
		assertThat(keys.getLast()).isEqualTo("/quote99");
	}

	@Test
	@DisplayName("후보 키도 10자에 맞춰 자른다")
	void candidateKeysTruncated() {
		List<String> keys = Shortcut.create("/견적요청서작성안내문").candidateKeys();

		assertThat(keys).startsWith("/견적요청서작성안내문", "/견적요청서작성안내2");
		assertThat(keys.getLast()).isEqualTo("/견적요청서작성안99");
	}

	@Test
	@DisplayName("숫자로 끝나는 9자 원본은 겹치는 후보를 빼 후보 키가 원본 포함 91개다")
	void candidateKeysWithoutOverlap() {
		List<String> keys = Shortcut.create("/견적요청서작성안1").candidateKeys();

		assertThat(keys).hasSize(91).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("숫자로 끝나는 10자 원본은 원본과 같아지는 후보를 빼 후보 키가 원본 포함 98개다")
	void candidateKeysWithoutOriginalDuplicate() {
		List<String> keys = Shortcut.create("/견적요청서작성안내2").candidateKeys();

		assertThat(keys).hasSize(98).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("자동 번호가 붙은 단축어는 원본에서 바뀐 것이다")
	void changedFromOriginal() {
		Shortcut original = Shortcut.create("/견적");

		Shortcut assigned = original.findAvailable(Set.of("/견적")).orElseThrow();

		assertThat(assigned.isChangedFrom(original)).isTrue();
	}

	@Test
	@DisplayName("원본 그대로 쓴 단축어는 바뀌지 않은 것이다")
	void notChangedFromOriginal() {
		Shortcut original = Shortcut.create("/견적");

		Shortcut assigned = original.findAvailable(Set.of()).orElseThrow();

		assertThat(assigned.isChangedFrom(original)).isFalse();
	}

	@Test
	@DisplayName("자모 분리형으로 만든 원본도 글자가 같으면 바뀌지 않은 것이다")
	void decomposedNotChanged() {
		Shortcut decomposed = Shortcut.create(Normalizer.normalize("/견적", Normalizer.Form.NFD));

		assertThat(Shortcut.create("/견적").isChangedFrom(decomposed)).isFalse();
	}

}
