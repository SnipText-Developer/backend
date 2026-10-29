package com.sniptext.domain.ai.support;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.sniptext.domain.ai.support.KeyInfoValue.DateValue;
import com.sniptext.domain.ai.support.KeyInfoValue.Quantity;
import com.sniptext.domain.ai.support.KeyInfoValue.TimeValue;

/**
 * 글에서 단위가 붙은 숫자를 뽑아 정규형으로 바꾼다. 단위 없는 숫자, 이어진 숫자열(전화번호·계좌번호), 한글로만 쓴 수는 뽑지 않는다.
 * 정규식 대신 앞으로만 읽는 방식이라 처리 시간이 글 길이에 비례한다 (3000자 입력에서 역추적 폭증이 없다).
 * 같은 위치에서는 긴 형식부터 맞추고(날짜 → 시각 → 범위 → 수량), 맞춘 구간은 다시 읽지 않는다.
 * 범위를 벗어난 날짜·시각(13월 5일, 25시)은 값 없이 구간만 소비해 안쪽 숫자가 따로 잡히지 않게 한다.
 */
final class KeyInfoExtractor {

	record Found(String text, KeyInfoValue value) {
	}

	private record Num(BigDecimal value, int end) {
	}

	/** 숫자와 한글 자릿수를 읽은 값. scale은 마지막 자릿수 배율, simple은 숫자 하나로만 이루어졌는지 (범위 앞쪽에 배율을 옮겨 붙일 수 있는지) */
	private record Amount(BigDecimal value, BigDecimal scale, boolean large, boolean simple, int numberEnd, int end) {
	}

	/** factor는 앞 숫자에 곱하면 정규형 값이 되는 배율 (15만 원이면 10,000, 3시간이면 60) */
	private record QuantityMatch(String unit, BigDecimal amount, BigDecimal factor, boolean simple, int numberEnd, int end) {
	}

	/** notFollowedBy로 시작하는 말이 바로 뒤에 붙으면 단위가 아니다 (번째, 번지, 인치 등) */
	private record Unit(String word, String canonical, int multiplier, boolean latin, List<String> notFollowedBy) {
	}

	private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);
	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);
	private static final BigDecimal HUNDRED_MILLION = BigDecimal.valueOf(100_000_000);
	private static final BigDecimal HALF_HOUR = BigDecimal.valueOf(30);

	private static final List<String> MORNING = List.of("오전", "새벽", "아침");
	private static final List<String> AFTERNOON = List.of("오후", "낮", "저녁", "밤");
	/** "3시 반에"처럼 반 뒤에 올 수 있는 말. 그 밖의 한글이 붙으면(반드시, 반대) 반이 아니다 */
	private static final List<String> AFTER_HALF = List.of("에", "까지", "부터", "쯤", "경", "정도", "으로", "이", "은", "입");
	private static final List<String> NOT_AFTER_HOUR = List.of("간", "리즈", "즌");
	private static final String TILDES = "~～∼";
	private static final String DIGIT_RUN_SEPARATORS = ".,:/-";

	/** 앞에 있는 단위부터 맞춘다. 겹치는 것은 긴 단위가 먼저다 (달러 → 달, 개월 → 개, 분기 → 분, 인분 → 인, kg → g) */
	private static final List<Unit> UNITS = List.of(
		alias("퍼센트", "%"), unit("%"), alias("프로", "%", "젝", "그", "필", "모", "세", "듀", "포", "덕", "토"),
		unit("달러"), unit("원"),
		unit("개월"), new Unit("시간", "분", 60, false, List.of()), unit("분기"), unit("인분"),
		unit("분", "의", "야", "할", "석"),
		unit("년"), unit("달"), unit("주", "년"), unit("일"), unit("박", "스"), unit("초", "과", "반", "등"),
		unit("명"), unit("개"), unit("건"), unit("회", "의실"), unit("번", "째", "지", "길"), unit("장"), unit("권"),
		unit("인", "치"), unit("층"), unit("평", "가", "균", "일"), unit("살"),
		latin("kg"), latin("km"), latin("cm"), latin("mm"), latin("ml"), latin("GB"), latin("MB"), latin("TB"),
		latin("g"), latin("m"), latin("L"));

	private final String text;
	private final List<Found> found = new ArrayList<>();

	private KeyInfoExtractor(String text) {
		this.text = text;
	}

	/** 뽑은 값을 글에 나온 순서대로 돌려준다. 입력은 먼저 NFC로 정규화한다 (맥 자모 분리형 대응). */
	static List<Found> extract(String text) {
		KeyInfoExtractor extractor = new KeyInfoExtractor(Normalizer.normalize(text, Normalizer.Form.NFC));
		extractor.scan();
		return extractor.found;
	}

	private void scan() {
		int i = 0;
		while (i < text.length()) {
			int end = matchAt(i);
			i = end > i ? end : i + 1;
		}
	}

	/** i에서 시작하는 값을 맞추고 끝 위치를 돌려준다. 맞지 않으면 -1 */
	private int matchAt(int i) {
		char c = charAt(i);
		if (c == '₩' || c == '$') {
			return prefixedCurrency(i);
		}
		if (meridiemAt(i) != null) {
			return time(i);
		}
		if (!isDigit(i) || !numberMayStart(i)) {
			return -1;
		}
		int end = numericDate(i);
		if (end < 0) {
			end = slashDate(i);
		}
		if (end < 0) {
			end = koreanDate(i);
		}
		if (end < 0) {
			end = time(i);
		}
		if (end < 0) {
			end = range(i);
		}
		if (end < 0) {
			end = quantity(i);
		}
		return end;
	}

	/** 앞에 숫자가 있거나 "숫자-", "숫자." 뒤면 이어진 숫자열의 일부라 새 값으로 보지 않는다 (02-123-4567번의 4567번) */
	private boolean numberMayStart(int i) {
		return !isDigit(i - 1) && !(isDigit(i - 2) && DIGIT_RUN_SEPARATORS.indexOf(charAt(i - 1)) >= 0);
	}

	/** 2026-10-14, 2026.10.14, 2026/10/14 */
	private int numericDate(int i) {
		int yearEnd = digitsEnd(i);
		char separator = charAt(yearEnd);
		if (yearEnd - i != 4 || "-./".indexOf(separator) < 0) {
			return -1;
		}
		int monthEnd = digitsEnd(yearEnd + 1);
		if (!within(yearEnd + 1, monthEnd, 2) || charAt(monthEnd) != separator) {
			return -1;
		}
		int dayEnd = digitsEnd(monthEnd + 1);
		if (!within(monthEnd + 1, dayEnd, 2) || continuesDigitRun(dayEnd)) {
			return -1;
		}
		addDate(i, dayEnd, parse(i, yearEnd), parse(yearEnd + 1, monthEnd), parse(monthEnd + 1, dayEnd));
		return dayEnd;
	}

	/** 연도 없는 10/14. 연도 없는 10.14는 소수와 구분할 수 없어 날짜로 보지 않는다 */
	private int slashDate(int i) {
		int monthEnd = digitsEnd(i);
		if (!within(i, monthEnd, 2) || charAt(monthEnd) != '/') {
			return -1;
		}
		int dayEnd = digitsEnd(monthEnd + 1);
		if (!within(monthEnd + 1, dayEnd, 2) || continuesDigitRun(dayEnd)) {
			return -1;
		}
		addDate(i, dayEnd, null, parse(i, monthEnd), parse(monthEnd + 1, dayEnd));
		return dayEnd;
	}

	/** 2026년 10월 14일, 2026년 10월, 10월 14일, 10월. 따로 떨어진 2026년은 단위 "년"으로 넘긴다 */
	private int koreanDate(int i) {
		int numberEnd = digitsEnd(i);
		Integer year = null;
		int monthStart = i;
		if (numberEnd - i == 4 && charAt(numberEnd) == '년') {
			year = parse(i, numberEnd);
			monthStart = skipSpaces(numberEnd + 1);
		}
		int monthEnd = digitsEnd(monthStart);
		if (!within(monthStart, monthEnd, 2) || charAt(monthEnd) != '월') {
			return -1;
		}
		int end = monthEnd + 1;
		Integer day = null;
		int dayStart = skipSpaces(end);
		int dayEnd = digitsEnd(dayStart);
		if (within(dayStart, dayEnd, 2) && charAt(dayEnd) == '일') {
			day = parse(dayStart, dayEnd);
			end = dayEnd + 1;
		}
		addDate(i, end, year, parse(monthStart, monthEnd), day);
		return end;
	}

	/** 일은 1~31만 본다. 월별 말일은 검사하지 않는다 */
	private void addDate(int start, int end, Integer year, int month, Integer day) {
		if (month >= 1 && month <= 12 && (day == null || (day >= 1 && day <= 31))) {
			add(start, end, new DateValue(year, month, day));
		}
	}

	/** 오후 3시, 15시, 3시 30분, 3시 반, 15:30, 오후 3:30 */
	private int time(int i) {
		String meridiem = meridiemAt(i);
		int hourStart = meridiem == null ? i : skipSpaces(i + meridiem.length());
		int hourEnd = digitsEnd(hourStart);
		if (!within(hourStart, hourEnd, 2)) {
			return -1;
		}
		int minute = 0;
		int end;
		if (charAt(hourEnd) == ':' && digitsEnd(hourEnd + 1) == hourEnd + 3 && !continuesDigitRun(hourEnd + 3)) {
			minute = parse(hourEnd + 1, hourEnd + 3);
			end = hourEnd + 3;
		} else if (isHourMark(hourEnd)) {
			end = hourEnd + 1;
			int minuteStart = skipSpaces(end);
			int minuteEnd = digitsEnd(minuteStart);
			if (within(minuteStart, minuteEnd, 2) && charAt(minuteEnd) == '분') {
				minute = parse(minuteStart, minuteEnd);
				end = minuteEnd + 1;
			} else if (isHalf(minuteStart)) {
				minute = 30;
				end = minuteStart + 1;
			}
		} else {
			return -1;
		}
		int hour = parse(hourStart, hourEnd);
		if (hour <= 24 && minute <= 59 && !(hour == 24 && minute > 0)) {
			add(i, end, new TimeValue(hours(meridiem, hour), minute));
		}
		return end;
	}

	/** 오전·오후가 없으면 1~12시는 h시와 h+12시 둘 다 될 수 있다 (12시는 0시와 12시). 오후·낮 12시는 정오, 그 밖의 12시는 자정이다 */
	private static Set<Integer> hours(String meridiem, int hour) {
		if (hour == 0 || hour > 12) {
			return Set.of(hour);
		}
		if (meridiem == null) {
			return Set.of(hour % 12, hour % 12 + 12);
		}
		if (hour == 12) {
			return Set.of("오후".equals(meridiem) || "낮".equals(meridiem) ? 12 : 0);
		}
		return Set.of(AFTERNOON.contains(meridiem) ? hour + 12 : hour);
	}

	/**
	 * 2~3일, 10~15만 원, 3~5시, 3-5일: 앞쪽 숫자에 뒤쪽 자릿수·단위를 붙여 둘 다 뽑는다.
	 * "-"는 양쪽이 세 자리 이하이고 뒤쪽 숫자 바로 뒤에 단위가 올 때만 범위로 본다 (전화번호와 구분).
	 */
	private int range(int i) {
		Num front = number(i);
		int separator = skipSpaces(front.end());
		boolean hyphen = TILDES.indexOf(charAt(separator)) < 0;
		int backStart;
		if (!hyphen) {
			backStart = skipSpaces(separator + 1);
		} else if (charAt(front.end()) == '-' && digitsEnd(i) == front.end() && front.end() - i <= 3) {
			backStart = front.end() + 1;
		} else {
			return -1;
		}
		int backDigitsEnd = digitsEnd(backStart);
		if (backDigitsEnd == backStart || (hyphen && backDigitsEnd - backStart > 3)) {
			return -1;
		}
		String frontNumber = text.substring(i, front.end());
		if (isHourMark(backDigitsEnd)) {
			return timeRange(i, frontNumber, backStart);
		}
		QuantityMatch back = quantityMatch(backStart);
		if (back == null || !back.simple() || (hyphen && isSpace(back.numberEnd()))) {
			return -1;
		}
		String frontText = frontNumber + text.substring(back.numberEnd(), back.end());
		found.add(new Found(frontText, new Quantity(back.unit(), front.value().multiply(back.factor()))));
		add(backStart, back.end(), new Quantity(back.unit(), back.amount()));
		return back.end();
	}

	private int timeRange(int i, String frontNumber, int backStart) {
		if (digitsEnd(i) - i != frontNumber.length() || frontNumber.length() > 2) {
			return -1;
		}
		int index = found.size();
		int end = time(backStart);
		int hour = Integer.parseInt(frontNumber);
		if (hour <= 24) {
			found.add(index, new Found(frontNumber + "시", new TimeValue(hours(null, hour), 0)));
		}
		return end;
	}

	/** ₩3,000,000, $50 */
	private int prefixedCurrency(int i) {
		Amount amount = amount(skipSpaces(i + 1));
		if (amount == null) {
			return -1;
		}
		add(i, amount.end(), new Quantity(charAt(i) == '₩' ? "원" : "달러", amount.value()));
		return amount.end();
	}

	private int quantity(int i) {
		QuantityMatch match = quantityMatch(i);
		if (match == null) {
			return -1;
		}
		add(i, match.end(), new Quantity(match.unit(), match.amount()));
		return match.end();
	}

	/** 수량 하나를 읽는다. 단위가 없으면 만·억이 들어간 큰 수만 인정한다. 시간 뒤의 분·반은 합쳐 분으로 센다 (3시간 30분 = 210분) */
	private QuantityMatch quantityMatch(int i) {
		Amount amount = amount(i);
		if (amount == null) {
			return null;
		}
		int unitStart = skipSpaces(amount.end());
		Unit unit = unitAt(unitStart);
		if (unit == null) {
			return amount.large()
				? new QuantityMatch(null, amount.value(), amount.scale(), amount.simple(), amount.numberEnd(), amount.end())
				: null;
		}
		BigDecimal multiplier = BigDecimal.valueOf(unit.multiplier());
		BigDecimal value = amount.value().multiply(multiplier);
		int end = unitStart + unit.word().length();
		boolean simple = amount.simple();
		if (unit.word().equals("시간")) {
			int minuteStart = skipSpaces(end);
			Num minute = number(minuteStart);
			Unit minuteUnit = minute == null ? null : unitAt(minute.end());
			if (minuteUnit != null && minuteUnit.word().equals("분")) {
				value = value.add(minute.value());
				end = minute.end() + 1;
				simple = false;
			} else if (isHalf(minuteStart)) {
				value = value.add(HALF_HOUR);
				end = minuteStart + 1;
				simple = false;
			}
		}
		return new QuantityMatch(unit.canonical(), value, amount.scale().multiply(multiplier), simple, amount.numberEnd(), end);
	}

	/**
	 * 숫자와 한글 자릿수를 읽는다. 1억 2천만 = 1억 + 2천×만, 2천5백만 = (2천 + 5백)×만, 3.5만 = 35,000.
	 * 띄어 쓴 다음 숫자는 자릿수가 붙을 때만 이어 읽는다 ("300만 2명"의 2명은 따로).
	 */
	private Amount amount(int i) {
		Num num = number(i);
		if (num == null) {
			return null;
		}
		int numberEnd = num.end();
		BigDecimal total = BigDecimal.ZERO;
		BigDecimal small = BigDecimal.ZERO;
		BigDecimal scale = BigDecimal.ONE;
		boolean large = false;
		int terms = 0;
		int end;
		while (true) {
			terms++;
			int scaleAt = skipSpaces(num.end());
			BigDecimal smallScale = smallScaleAt(scaleAt);
			BigDecimal largeScale;
			if (smallScale != null) {
				small = small.add(num.value().multiply(smallScale));
				scale = smallScale;
				end = scaleAt + 1;
				largeScale = largeScaleAt(end);
				if (largeScale != null) {
					end++;
				}
			} else {
				largeScale = largeScaleAt(scaleAt);
				if (largeScale == null) {
					small = small.add(num.value());
					scale = BigDecimal.ONE;
					end = num.end();
					break;
				}
				small = small.add(num.value());
				scale = BigDecimal.ONE;
				end = scaleAt + 1;
			}
			if (largeScale != null) {
				total = total.add(small.multiply(largeScale));
				small = BigDecimal.ZERO;
				scale = scale.multiply(largeScale);
				large = true;
			}
			int next = skipSpaces(end);
			if (!isDigit(next) || (next > end && !scaledNumberAt(next))) {
				break;
			}
			num = number(next);
		}
		return new Amount(total.add(small), scale, large, terms == 1, numberEnd, end);
	}

	private boolean scaledNumberAt(int i) {
		int scaleAt = skipSpaces(number(i).end());
		return smallScaleAt(scaleAt) != null || largeScaleAt(scaleAt) != null;
	}

	private BigDecimal smallScaleAt(int i) {
		return switch (charAt(i)) {
			case '천' -> THOUSAND;
			case '백' -> HUNDRED;
			default -> null;
		};
	}

	/** "3만큼"의 만은 자릿수가 아니다 */
	private BigDecimal largeScaleAt(int i) {
		return switch (charAt(i)) {
			case '만' -> text.startsWith("큼", i + 1) ? null : TEN_THOUSAND;
			case '억' -> HUNDRED_MILLION;
			default -> null;
		};
	}

	/** 세 자리 쉼표가 맞는 수(3,000,000) 또는 쉼표 없는 수, 그리고 소수 부분 */
	private Num number(int i) {
		int end = digitsEnd(i);
		if (end == i) {
			return null;
		}
		if (end - i <= 3) {
			while (charAt(end) == ',' && digitsEnd(end + 1) == end + 4) {
				end += 4;
			}
		}
		if (charAt(end) == '.' && isDigit(end + 1)) {
			end = digitsEnd(end + 1);
		}
		return new Num(new BigDecimal(text.substring(i, end).replace(",", "")), end);
	}

	private Unit unitAt(int i) {
		for (Unit unit : UNITS) {
			int next = i + unit.word().length();
			if (text.startsWith(unit.word(), i)
				&& !startsWithAny(next, unit.notFollowedBy())
				&& !(unit.latin() && isAsciiLetter(next))) {
				return unit;
			}
		}
		return null;
	}

	private String meridiemAt(int i) {
		for (String word : MORNING) {
			if (text.startsWith(word, i)) {
				return word;
			}
		}
		for (String word : AFTERNOON) {
			if (text.startsWith(word, i)) {
				return word;
			}
		}
		return null;
	}

	/** 숫자 뒤의 "시"가 시각 표시인지. 3시간·3시리즈·2시즌은 아니다 */
	private boolean isHourMark(int i) {
		return charAt(i) == '시' && !startsWithAny(i + 1, NOT_AFTER_HOUR);
	}

	private boolean isHalf(int i) {
		if (charAt(i) != '반') {
			return false;
		}
		char next = charAt(i + 1);
		return next < '가' || next > '힣' || startsWithAny(i + 1, AFTER_HALF);
	}

	/** 날짜·시각 바로 뒤에 "-숫자", ":숫자"가 더 이어지면 더 긴 숫자열의 일부다 */
	private boolean continuesDigitRun(int i) {
		return DIGIT_RUN_SEPARATORS.indexOf(charAt(i)) >= 0 && isDigit(i + 1);
	}

	private boolean startsWithAny(int i, List<String> words) {
		for (String word : words) {
			if (text.startsWith(word, i)) {
				return true;
			}
		}
		return false;
	}

	private void add(int start, int end, KeyInfoValue value) {
		found.add(new Found(text.substring(start, end), value));
	}

	private int digitsEnd(int i) {
		int end = i;
		while (isDigit(end)) {
			end++;
		}
		return end;
	}

	private int skipSpaces(int i) {
		int end = i;
		while (isSpace(end)) {
			end++;
		}
		return end;
	}

	private static boolean within(int start, int end, int maxLength) {
		return end > start && end - start <= maxLength;
	}

	private int parse(int start, int end) {
		return Integer.parseInt(text.substring(start, end));
	}

	private boolean isDigit(int i) {
		char c = charAt(i);
		return c >= '0' && c <= '9';
	}

	private boolean isSpace(int i) {
		char c = charAt(i);
		return c == ' ' || c == '\t' || c == ' ';
	}

	private boolean isAsciiLetter(int i) {
		char c = charAt(i);
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
	}

	/** 범위 밖이면 '\0'. 경계 검사를 한곳에 모은다 */
	private char charAt(int i) {
		return i >= 0 && i < text.length() ? text.charAt(i) : '\0';
	}

	private static Unit unit(String word, String... notFollowedBy) {
		return new Unit(word, word, 1, false, List.of(notFollowedBy));
	}

	private static Unit alias(String word, String canonical, String... notFollowedBy) {
		return new Unit(word, canonical, 1, false, List.of(notFollowedBy));
	}

	private static Unit latin(String word) {
		return new Unit(word, word, 1, true, List.of());
	}

}
