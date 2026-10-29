package com.sniptext.domain.ai.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class KeyInfoCheckerTest {

	/** 양쪽 방향 모두 빠진 값·새로 생긴 값이 없어야 한다. */
	private static void assertSameValues(String first, String second) {
		assertNoDifference(KeyInfoChecker.compare(first, second));
		assertNoDifference(KeyInfoChecker.compare(second, first));
	}

	private static void assertNoDifference(KeyInfoDiff diff) {
		assertThat(diff.missingValues()).isEmpty();
		assertThat(diff.addedValues()).isEmpty();
		assertThat(diff.hasDifference()).isFalse();
	}

	/** 결과를 빈 글로 두면 원문에서 뽑은 값이 전부 빠진 값으로 나온다. 추출 결과를 공개 메서드로 확인하는 방법이다. */
	private static List<String> extracted(String text) {
		return KeyInfoChecker.compare(text, "").missingValues();
	}

	@Nested
	@DisplayName("같은 값의 다른 표기는 차이가 없다")
	class SameValue {

		@Test
		@DisplayName("300만 원, 3,000,000원, 300만원, ₩3,000,000은 서로 모두 같다")
		void wonNotations() {
			List<String> notations = List.of("300만 원", "3,000,000원", "300만원", "₩3,000,000");
			for (String first : notations) {
				for (String second : notations) {
					assertSameValues(first, second);
				}
			}
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"1억 2천만 원|120,000,000원",
			"3.5만 원|35,000원",
			"2천5백만 원|25,000,000원",
			"50달러|$50",
			"10월 14일|10/14",
			"20%|20퍼센트",
			"20%|20프로",
			"300 만 원|300만 원",
			"3 개월|3개월",
			"3시간|180분",
			"1시간 반|90분",
			"3시간 30분|210분",
			"저녁 7시|19시",
			"오전 12시|0시",
			"밤 12시|0시",
			"낮 12시|오후 12시",
		})
		@DisplayName("두 표기를 서로 바꿔도 같은 값이다")
		void pairs(String first, String second) {
			assertSameValues(first, second);
		}

		@Test
		@DisplayName("2026년 10월 14일, 2026.10.14, 2026/10/14, 2026-10-14는 서로 모두 같다")
		void fullDateNotations() {
			List<String> notations = List.of("2026년 10월 14일", "2026.10.14", "2026/10/14", "2026-10-14");
			for (String first : notations) {
				for (String second : notations) {
					assertSameValues(first, second);
				}
			}
		}

		@Test
		@DisplayName("오후 3시, 15시, 15:00은 서로 모두 같다")
		void timeNotations() {
			List<String> notations = List.of("오후 3시", "15시", "15:00");
			for (String first : notations) {
				for (String second : notations) {
					assertSameValues(first, second);
				}
			}
		}

		@Test
		@DisplayName("오후 3시 30분, 15:30, 오후 3시 반, 오후 3:30은 서로 모두 같다")
		void timeWithMinuteNotations() {
			List<String> notations = List.of("오후 3시 30분", "15:30", "오후 3시 반", "오후 3:30");
			for (String first : notations) {
				for (String second : notations) {
					assertSameValues(first, second);
				}
			}
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"300만|300만 원",
			"1억 2천만|120,000,000원",
			"300만|3,000,000원",
		})
		@DisplayName("단위 없는 큰 수는 같은 값이면 단위가 있는 값과 같다")
		void bareLargeNumberEqualsUnitValue(String bare, String withUnit) {
			assertSameValues(bare, withUnit);
		}

		@Test
		@DisplayName("자모 분리형(NFD)으로 쓴 날짜도 완성형과 같은 값이다")
		void decomposedHangul() {
			String decomposed = Normalizer.normalize("10월 14일", Normalizer.Form.NFD);

			assertSameValues(decomposed, "10월 14일");
		}

	}

	@Nested
	@DisplayName("빠진 값")
	class Missing {

		@Test
		@DisplayName("명세 예시: 날짜·금액이 결과에서 모두 빠지면 원문 표기 그대로 순서대로 담는다")
		void specExample() {
			KeyInfoDiff diff = KeyInfoChecker.compare(
				"10월 14일까지 300만 원으로 진행 가능",
				"안녕하세요. 말씀하신 조건으로 진행 가능함을 알려드립니다.");

			assertThat(diff.missingValues()).containsExactly("10월 14일", "300만 원");
			assertThat(diff.addedValues()).isEmpty();
			assertThat(diff.hasDifference()).isTrue();
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"300만 원|350만 원",
			"10월 14일|10월 15일",
			"오후 3시|오전 3시",
			"5명|6명",
			"3개월|3주",
			"300만|350만 원",
		})
		@DisplayName("값이 바뀌면 원래 값은 빠진 값, 바뀐 값은 새로 생긴 값이다")
		void changed(String original, String corrected) {
			KeyInfoDiff diff = KeyInfoChecker.compare(original, corrected);

			assertThat(diff.missingValues()).containsExactly(original);
			assertThat(diff.addedValues()).containsExactly(corrected);
		}

		@Test
		@DisplayName("10월 14일이 10월로 덜 구체적으로 바뀌면 빠진 값만 있다")
		void lessSpecific() {
			KeyInfoDiff diff = KeyInfoChecker.compare("10월 14일까지", "10월 중으로");

			assertThat(diff.missingValues()).containsExactly("10월 14일");
			assertThat(diff.addedValues()).isEmpty();
		}

	}

	@Nested
	@DisplayName("새로 생긴 값")
	class Added {

		@Test
		@DisplayName("원문에 없던 금액·날짜가 결과에 생기면 결과 표기 그대로 담는다")
		void newValues() {
			KeyInfoDiff diff = KeyInfoChecker.compare(
				"곧 연락드리겠습니다",
				"10월 14일까지 300만원으로 연락드리겠습니다");

			assertThat(diff.missingValues()).isEmpty();
			assertThat(diff.addedValues()).containsExactly("10월 14일", "300만원");
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"10월|10월 14일",
			"3시|오후 3시",
			"10월 14일|2026년 10월 14일",
			"10월 14일|2026-10-14",
			"3시|15시",
		})
		@DisplayName("연도·일·오전오후를 새로 채우면 빠진 값은 없고 새로 생긴 값이다")
		void moreSpecific(String original, String corrected) {
			KeyInfoDiff diff = KeyInfoChecker.compare(original, corrected);

			assertThat(diff.missingValues()).isEmpty();
			assertThat(diff.addedValues()).containsExactly(corrected);
		}

	}

	@Nested
	@DisplayName("순서와 중복")
	class OrderAndDuplicates {

		@Test
		@DisplayName("원문에 같은 값이 두 표기로 나오고 결과에서 빠지면 처음 표기 하나만 담는다")
		void duplicateInOriginal() {
			KeyInfoDiff diff = KeyInfoChecker.compare("300만 원, 즉 3,000,000원입니다", "확인 부탁드립니다");

			assertThat(diff.missingValues()).containsExactly("300만 원");
		}

		@Test
		@DisplayName("원문에 두 번, 결과에 한 번 있으면 빠진 것이 아니다 (개수는 비교하지 않는다)")
		void countIsNotCompared() {
			assertNoDifference(KeyInfoChecker.compare("300만 원, 다시 말해 300만 원", "300만원입니다"));
		}

		@Test
		@DisplayName("여러 값이 빠지면 원문에 나온 순서대로 담는다")
		void originalOrder() {
			KeyInfoDiff diff = KeyInfoChecker.compare("오후 3시에 10월 14일 일정, 300만 원, 5명", "일정 확인 부탁드립니다");

			assertThat(diff.missingValues()).containsExactly("오후 3시", "10월 14일", "300만 원", "5명");
		}

	}

	@Nested
	@DisplayName("범위 표기")
	class Ranges {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"2~3일|2일|3일",
			"10~15만 원|10만 원|15만 원",
			"3시~5시|3시|5시",
			"3~5시|3시|5시",
			"3-5일|3일|5일",
			"2～3주|2주|3주",
		})
		@DisplayName("앞쪽 숫자에 뒤쪽 단위를 붙여 둘 다 뽑는다")
		void bothEnds(String text, String front, String back) {
			assertThat(extracted(text)).containsExactly(front, back);
		}

		@Test
		@DisplayName("원문 \"3~5일 소요\", 결과 \"5일 소요\"이면 빠진 값은 3일이다")
		void frontMissing() {
			KeyInfoDiff diff = KeyInfoChecker.compare("3~5일 소요", "5일 소요");

			assertThat(diff.missingValues()).containsExactly("3일");
			assertThat(diff.addedValues()).isEmpty();
		}

		@Test
		@DisplayName("범위를 풀어 쓴 표기와 같은 값이다")
		void sameAsSpelledOut() {
			assertSameValues("10~15만 원", "10만 원에서 15만 원 사이");
		}

	}

	@Nested
	@DisplayName("뽑는 것과 뽑지 않는 것")
	class Extraction {

		@ParameterizedTest
		@ValueSource(strings = {
			"3가지 중 2번째",
			"1부터 5까지",
			"전년 대비 3대비",
			"2호선",
			"3세트",
			"2차이",
			"12점심",
			"1번지",
			"12번길",
			"1회의실",
			"27인치",
			"5주년",
			"3박스",
			"30초과",
			"3평균",
			"3분의 1",
			"3시리즈",
			"3만큼",
			"010-1234-5678",
			"02-123-4567",
			"02-123-4567번",
			"123-456-789012",
			"10.5",
			"10.14",
			"삼백만 원",
			"만 원",
			"13월 5일",
			"10월 32일",
			"25시",
			"3시 70분",
			"3:2",
			"숫자 없는 문장입니다",
		})
		@DisplayName("단위가 없거나 단위가 아닌 말, 이어진 숫자열, 범위를 벗어난 값은 뽑지 않는다")
		void notExtracted(String text) {
			assertThat(extracted(text)).isEmpty();
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"3일부터 시작|3일",
			"3층 회의실|3층",
			"2번 출구|2번",
			"1인당|1인",
			"2인분|2인분",
			"30평|30평",
			"5살|5살",
			"3초|3초",
			"2권|2권",
			"3장|3장",
			"4건|4건",
			"2회|2회",
			"5개|5개",
			"5명예약|5명",
			"3달|3달",
			"1박|1박",
			"2주차|2주",
			"20kg|20kg",
			"2026년|2026년",
		})
		@DisplayName("단위 목록의 단위는 뒤에 조사나 말이 붙어도 단위까지만 뽑는다")
		void extractedUnit(String text, String expected) {
			assertThat(extracted(text)).containsExactly(expected);
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"3시간 30분 걸려요|3시간 30분",
			"오후 3시 30분에|오후 3시 30분",
			"10월 14일까지|10월 14일",
			"3시 반드시 와|3시",
			"1박 2일|1박",
		})
		@DisplayName("겹치는 표기는 긴 형식 하나로 잡고 안쪽 숫자를 따로 또 잡지 않는다")
		void overlapping(String text, String first) {
			assertThat(extracted(text)).first().isEqualTo(first);
		}

		@Test
		@DisplayName("겹치는 표기마다 값이 하나씩만 나온다")
		void overlappingCount() {
			assertThat(extracted("3시간 30분")).hasSize(1);
			assertThat(extracted("오후 3시 30분")).hasSize(1);
			assertThat(extracted("10월 14일")).hasSize(1);
			assertThat(extracted("1박 2일")).containsExactly("1박", "2일");
		}

	}

	@Nested
	@DisplayName("실제 교정 쌍")
	class RealPairs {

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"이번 달은 일정이 꽉 차서 외주 못 받는다고 해줘|아쉽게도 이번 달은 일정이 모두 차 있어 새로운 외주를 맡기 어려운 상황입니다.",
			"내일 3시 회의 10분 늦을 것 같아요|내일 3시 회의에 10분 정도 늦을 것 같습니다. 양해 부탁드립니다.",
			"견적은 300만원이고 2주 걸려요|견적 금액은 300만 원이며, 작업 기간은 2주 소요됩니다.",
			"5명 예약했고 저녁 7시예요|총 5명으로 저녁 7시에 예약해 두었습니다.",
		})
		@DisplayName("핵심 정보가 그대로인 교정은 경고가 없다")
		void noWarning(String original, String corrected) {
			assertNoDifference(KeyInfoChecker.compare(original, corrected));
		}

		@Test
		@DisplayName("금액과 기간이 바뀐 교정은 바뀐 값을 모두 잡는다")
		void warning() {
			KeyInfoDiff diff = KeyInfoChecker.compare(
				"견적은 300만원이고 2주 걸려요",
				"견적 금액은 350만 원이며 작업 기간은 3주입니다.");

			assertThat(diff.missingValues()).containsExactly("300만원", "2주");
			assertThat(diff.addedValues()).containsExactly("350만 원", "3주");
		}

	}

	@Nested
	@DisplayName("경계")
	class Boundaries {

		@ParameterizedTest
		@CsvSource(delimiter = '|', emptyValue = "", value = {
			"안녕하세요|반갑습니다",
			"''|''",
			"''|안녕하세요",
		})
		@DisplayName("숫자가 없거나 빈 문자열이면 차이가 없다")
		void noNumbers(String original, String corrected) {
			assertNoDifference(KeyInfoChecker.compare(original, corrected));
		}

		@Test
		@DisplayName("null이면 IllegalArgumentException이고 메시지에 다른 쪽 입력이 들어가지 않는다")
		void nullInput() {
			assertThatThrownBy(() -> KeyInfoChecker.compare(null, "300만 원"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageNotContaining("300만");
			assertThatThrownBy(() -> KeyInfoChecker.compare("300만 원", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageNotContaining("300만");
		}

		@Test
		@DisplayName("새로 생긴 값만 있어도 차이가 있다")
		void addedOnlyHasDifference() {
			assertThat(KeyInfoChecker.compare("", "5명").hasDifference()).isTrue();
		}

		@Test
		@DisplayName("값 수십 개가 든 3000자 원문과 결과를 100ms 안에 비교한다")
		void longTextFinishesQuickly() {
			String original = "10월 14일까지 300만 원, 오후 3시 30분에 5명이 2~3일 머뭅니다. ".repeat(80).substring(0, 3000);
			String corrected = original.replace("300만 원", "350만원").replace("5명", "6명");
			assertThat(original).hasSize(3000);
			KeyInfoChecker.compare("300만 원", "300만 원");

			KeyInfoDiff diff = assertTimeout(Duration.ofMillis(100), () -> KeyInfoChecker.compare(original, corrected));

			assertThat(diff.missingValues()).containsExactly("300만 원", "5명");
			assertThat(diff.addedValues()).containsExactly("350만원", "6명");
		}

		@ParameterizedTest
		@ValueSource(strings = {"1,", "1:", "1-", "1~", "1.", "1/", "1억 ", "1억", "1 ", "오후 ", "3시 ", "1시간 "})
		@DisplayName("같은 조각만 반복한 3000자 글도 100ms 안에 끝난다")
		void repeatedPiecesFinishQuickly(String piece) {
			String text = piece.repeat(3000 / piece.length());
			KeyInfoChecker.compare("300만 원", "300만 원");

			assertTimeout(Duration.ofMillis(100), () -> KeyInfoChecker.compare(text, text));
		}

		@Test
		@DisplayName("돌려준 목록은 수정할 수 없다")
		void unmodifiable() {
			KeyInfoDiff diff = KeyInfoChecker.compare("300만 원", "350만 원");

			assertThatThrownBy(() -> diff.missingValues().add("5명"))
				.isInstanceOf(UnsupportedOperationException.class);
			assertThatThrownBy(() -> diff.addedValues().add("5명"))
				.isInstanceOf(UnsupportedOperationException.class);
		}

		@Test
		@DisplayName("만들 때 넘긴 목록을 나중에 고쳐도 결과는 바뀌지 않는다")
		void defensiveCopy() {
			List<String> missing = new ArrayList<>(List.of("300만 원"));
			KeyInfoDiff diff = new KeyInfoDiff(missing, List.of());

			missing.add("5명");

			assertThat(diff.missingValues()).containsExactly("300만 원");
		}

		@Test
		@DisplayName("toString에는 개수만 나오고 원문 조각은 나오지 않는다")
		void toStringHidesValues() {
			KeyInfoDiff diff = KeyInfoChecker.compare("10월 14일까지 300만 원", "350만 원");

			assertThat(diff.toString())
				.doesNotContain("10월", "300만", "350만")
				.contains("missing=2", "added=1");
		}

	}

}
