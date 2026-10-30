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
 * 범위(~, -, –, 부터…까지)의 앞쪽 값은 뒤쪽 값의 자릿수·단위를 붙여 읽고, 뒤쪽 값은 앞쪽의 연·월·오전오후를 물려받는다.
 * 뒤쪽이 범위를 벗어나면 범위 전체를 버린다.
 */
final class KeyInfoExtractor {

	record Found(String text, KeyInfoValue value) {
	}

	private record Num(BigDecimal value, int end) {
	}

	/** 형식만 맞춘 날짜. 일은 1~31만 본다. 월별 말일은 검사하지 않는다. dayStart는 "10월 14일"의 14 위치(한글 날짜만, 아니면 -1) */
	private record DateMatch(Integer year, int month, Integer day, int dayStart, int end) {

		boolean valid() {
			return month >= 1 && month <= 12 && (day == null || (day >= 1 && day <= 31));
		}

		DateValue toValue() {
			return new DateValue(year, month, day);
		}

		/** 연도가 없으면 앞쪽 값의 연도를 물려받는다 */
		DateMatch inherit(DateMatch front) {
			return year == null ? new DateMatch(front.year(), month, day, dayStart, end) : this;
		}

	}

	/** 형식만 맞춘 시각. colon은 15:30처럼 쌍점으로 썼는지 */
	private record TimeMatch(String meridiem, int hour, int minute, boolean colon, int end) {

		boolean valid() {
			return validTime(hour, minute);
		}

		TimeValue toValue() {
			return new TimeValue(hours(meridiem, hour), minute);
		}

		/** 오전·오후가 없는 1~12시는 앞쪽 값의 것을 물려받는다. 앞쪽보다 이른 시면 반대쪽으로 넘어간 것으로 본다 (오전 10시~2시의 2시는 오후) */
		TimeMatch inherit(TimeMatch front) {
			if (meridiem != null || front.meridiem() == null || hour == 0 || hour > 12) {
				return this;
			}
			String inherited = front.meridiem();
			if (hour % 12 < front.hour() % 12) {
				inherited = MORNING.contains(inherited) ? "오후" : "오전";
			}
			return new TimeMatch(inherited, hour, minute, colon, end);
		}

	}

	/** 범위 기호 뒤 값의 시작 위치. hyphen은 하이픈으로 이었는지, until은 뒤쪽 값 바로 뒤에 "까지"가 와야 하는지 */
	private record Connector(int backStart, boolean hyphen, boolean until) {
	}

	/**
	 * 숫자와 한글 자릿수를 읽은 값. scale은 마지막 자릿수 배율, simple은 숫자 하나로만 이루어졌는지 (범위 앞쪽에 배율을 옮겨 붙일 수 있는지).
	 * largeScale은 마지막 만·억 배율(없으면 1), largeStart는 그 글자의 위치(없으면 end)다.
	 */
	private record Amount(BigDecimal value, BigDecimal scale, BigDecimal largeScale, boolean large, boolean simple,
		int numberEnd, int largeStart, int end) {
	}

	/** multiplier는 단위 배율 (3시간이면 60), number는 단위 앞의 수 */
	private record QuantityMatch(String unit, BigDecimal amount, BigDecimal multiplier, Amount number, boolean simple, int end) {
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
	private static final String HYPHENS = "-–";
	/** "3시부터 5시까지"처럼 앞쪽 값 바로 뒤에 붙어 범위를 여는 말. 뒤쪽 값 바로 뒤에 UNTIL이 와야 범위다 */
	private static final List<String> FROM = List.of("부터", "에서");
	private static final String UNTIL = "까지";
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
		int end = fullDate(i);
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

	/** 숫자로만 쓴 날짜. 10/14-10/16처럼 하이픈 양쪽이 모두 완전한 날짜면 범위로 본다 */
	private int fullDate(int i) {
		DateMatch front = fullDateAt(i);
		if (front == null) {
			return -1;
		}
		int rangeEnd = dateRange(i, front, true);
		if (rangeEnd >= 0) {
			return rangeEnd;
		}
		if (continuesDigitRun(front.end())) {
			return -1;
		}
		addDate(i, front);
		return front.end();
	}

	/**
	 * 완전한 앞쪽 날짜 뒤의 범위(~, 부터…까지, 숫자 날짜끼리는 하이픈)를 읽어 둘 다 뽑는다. 범위가 아니면 -1.
	 * 뒤쪽은 연도가 없으면 앞쪽 연도를, 한글 날짜 뒤에 일만 쓰면(10월 14일~16일) 연·월을 물려받는다. 뒤쪽이 범위를 벗어나면 둘 다 버린다.
	 */
	private int dateRange(int start, DateMatch front, boolean hyphenAllowed) {
		Connector connector = connectorAt(front.end(), hyphenAllowed, true);
		if (connector == null) {
			return -1;
		}
		int backStart = connector.backStart();
		DateMatch back = fullDateAt(backStart);
		boolean dayOnly = false;
		if (back != null) {
			if (continuesDigitRun(back.end())) {
				return -1;
			}
		} else if (!connector.hyphen()) {
			back = koreanDateAt(backStart);
			if (back == null && front.day() != null && front.dayStart() >= 0) {
				back = dayAt(backStart, front);
				dayOnly = true;
			}
		}
		if (back == null || !closes(connector, back.end())) {
			return -1;
		}
		back = back.inherit(front);
		if (back.valid()) {
			addDate(start, front);
			String prefix = dayOnly ? text.substring(start, front.dayStart()) : "";
			found.add(new Found(prefix + text.substring(backStart, back.end()), back.toValue()));
		}
		return back.end();
	}

	/** 뒤쪽에 일만 쓴 16일. 연·월은 앞쪽 값을 따른다 */
	private DateMatch dayAt(int i, DateMatch front) {
		int dayEnd = digitsEnd(i);
		if (!within(i, dayEnd, 2) || charAt(dayEnd) != '일') {
			return null;
		}
		return new DateMatch(front.year(), front.month(), parse(i, dayEnd), -1, dayEnd + 1);
	}

	private DateMatch fullDateAt(int i) {
		DateMatch date = numericDateAt(i);
		return date != null ? date : slashDateAt(i);
	}

	/** 2026-10-14, 2026.10.14, 2026/10/14 */
	private DateMatch numericDateAt(int i) {
		int yearEnd = digitsEnd(i);
		char separator = charAt(yearEnd);
		if (yearEnd - i != 4 || "-./".indexOf(separator) < 0) {
			return null;
		}
		int monthEnd = digitsEnd(yearEnd + 1);
		if (!within(yearEnd + 1, monthEnd, 2) || charAt(monthEnd) != separator) {
			return null;
		}
		int dayEnd = digitsEnd(monthEnd + 1);
		if (!within(monthEnd + 1, dayEnd, 2)) {
			return null;
		}
		return new DateMatch(parse(i, yearEnd), parse(yearEnd + 1, monthEnd), parse(monthEnd + 1, dayEnd), -1, dayEnd);
	}

	/** 연도 없는 10/14. 연도 없는 10.14는 소수와 구분할 수 없어 날짜로 보지 않는다 */
	private DateMatch slashDateAt(int i) {
		int monthEnd = digitsEnd(i);
		if (!within(i, monthEnd, 2) || charAt(monthEnd) != '/') {
			return null;
		}
		int dayEnd = digitsEnd(monthEnd + 1);
		if (!within(monthEnd + 1, dayEnd, 2)) {
			return null;
		}
		return new DateMatch(null, parse(i, monthEnd), parse(monthEnd + 1, dayEnd), -1, dayEnd);
	}

	/** 2026년 10월 14일, 2026년 10월, 10월 14일, 10월, 10월 14~16일, 10월 14일~16일 */
	private int koreanDate(int i) {
		DateMatch front = koreanDateAt(i);
		if (front == null) {
			return -1;
		}
		int rangeEnd = front.day() == null ? dayRange(i, front) : -1;
		if (rangeEnd < 0) {
			rangeEnd = dateRange(i, front, false);
		}
		if (rangeEnd >= 0) {
			return rangeEnd;
		}
		addDate(i, front);
		return front.end();
	}

	/** 2026년 10월 14일, 2026년 10월, 10월 14일, 10월. 따로 떨어진 2026년은 단위 "년"으로 넘긴다 */
	private DateMatch koreanDateAt(int i) {
		int numberEnd = digitsEnd(i);
		Integer year = null;
		int monthStart = i;
		if (numberEnd - i == 4 && charAt(numberEnd) == '년') {
			year = parse(i, numberEnd);
			monthStart = skipSpaces(numberEnd + 1);
		}
		int monthEnd = digitsEnd(monthStart);
		if (!within(monthStart, monthEnd, 2) || charAt(monthEnd) != '월') {
			return null;
		}
		int month = parse(monthStart, monthEnd);
		int dayStart = skipSpaces(monthEnd + 1);
		int dayEnd = digitsEnd(dayStart);
		if (within(dayStart, dayEnd, 2) && charAt(dayEnd) == '일') {
			return new DateMatch(year, month, parse(dayStart, dayEnd), dayStart, dayEnd + 1);
		}
		return new DateMatch(year, month, null, dayStart, monthEnd + 1);
	}

	/** 10월 14~16일: 앞쪽 일에 뒤쪽의 "일"을 붙이고, 양쪽 모두 앞에 쓴 연·월로 읽는다. 뒤쪽이 범위를 벗어나면 월까지 모두 버린다 */
	private int dayRange(int start, DateMatch month) {
		int dayStart = month.dayStart();
		int dayEnd = digitsEnd(dayStart);
		if (!within(dayStart, dayEnd, 2)) {
			return -1;
		}
		Connector connector = connectorAt(dayEnd, true, false);
		if (connector == null) {
			return -1;
		}
		int backStart = connector.backStart();
		int backEnd = digitsEnd(backStart);
		if (!within(backStart, backEnd, 2) || charAt(backEnd) != '일') {
			return -1;
		}
		DateMatch back = new DateMatch(month.year(), month.month(), parse(backStart, backEnd), -1, backEnd + 1);
		if (back.valid()) {
			DateMatch front = new DateMatch(month.year(), month.month(), parse(dayStart, dayEnd), dayStart, dayEnd);
			if (front.valid()) {
				found.add(new Found(text.substring(start, dayEnd) + "일", front.toValue()));
			}
			found.add(new Found(text.substring(start, dayStart) + text.substring(backStart, back.end()), back.toValue()));
		}
		return back.end();
	}

	private void addDate(int start, DateMatch date) {
		if (date.valid()) {
			add(start, date.end(), date.toValue());
		}
	}

	/** 오후 3시, 15시, 3시 30분, 3시 반, 15:30, 오후 3:30, 오후 2~4시, 3시부터 5시까지, 09:00-18:00 */
	private int time(int i) {
		TimeMatch front = timeAt(i);
		if (front == null) {
			return meridiemTimeRange(i);
		}
		int rangeEnd = timeRange(i, front, text.substring(i, front.end()), false);
		if (rangeEnd >= 0) {
			return rangeEnd;
		}
		if (front.colon() && continuesDigitRun(front.end())) {
			return -1;
		}
		if (front.valid()) {
			add(i, front.end(), front.toValue());
		}
		return front.end();
	}

	/** [오전·오후] H시[ M분 | 반], [오전·오후] H:mm의 형식만 맞춘다. 맞지 않으면 null */
	private TimeMatch timeAt(int i) {
		String meridiem = meridiemAt(i);
		int hourStart = meridiem == null ? i : skipSpaces(i + meridiem.length());
		int hourEnd = digitsEnd(hourStart);
		if (!within(hourStart, hourEnd, 2)) {
			return null;
		}
		int hour = parse(hourStart, hourEnd);
		if (isColonMinute(hourEnd)) {
			return new TimeMatch(meridiem, hour, parse(hourEnd + 1, hourEnd + 3), true, hourEnd + 3);
		}
		if (!isHourMark(hourEnd)) {
			return null;
		}
		int minuteStart = skipSpaces(hourEnd + 1);
		int minuteEnd = digitsEnd(minuteStart);
		if (within(minuteStart, minuteEnd, 2) && charAt(minuteEnd) == '분') {
			return new TimeMatch(meridiem, hour, parse(minuteStart, minuteEnd), false, minuteEnd + 1);
		}
		if (isHalf(minuteStart)) {
			return new TimeMatch(meridiem, hour, 30, false, minuteStart + 1);
		}
		return new TimeMatch(meridiem, hour, 0, false, hourEnd + 1);
	}

	/** 오후 2~4시: 오전·오후 뒤 시가 없는 앞쪽 숫자에 "시"를 붙여 범위로 읽는다 */
	private int meridiemTimeRange(int i) {
		String meridiem = meridiemAt(i);
		if (meridiem == null) {
			return -1;
		}
		int hourStart = skipSpaces(i + meridiem.length());
		int hourEnd = digitsEnd(hourStart);
		if (!within(hourStart, hourEnd, 2)) {
			return -1;
		}
		TimeMatch front = new TimeMatch(meridiem, parse(hourStart, hourEnd), 0, false, hourEnd);
		return timeRange(i, front, text.substring(i, hourEnd) + "시", true);
	}

	/**
	 * 앞쪽 시각 뒤의 범위(~, 부터…까지, H:mm끼리는 하이픈)를 읽어 둘 다 뽑는다. 범위가 아니면 -1.
	 * shortFront는 "오후 2~4시"의 2처럼 시가 없는 앞쪽이다. 이때는 하이픈도 보고 부터…까지는 보지 않으며, 뒤쪽은 오전·오후 없는 H시여야 한다.
	 * 뒤쪽은 오전·오후가 없으면 앞쪽 것을 물려받는다. 뒤쪽이 범위를 벗어나면(3~25시) 둘 다 버린다.
	 */
	private int timeRange(int start, TimeMatch front, String frontText, boolean shortFront) {
		Connector connector = connectorAt(front.end(), shortFront || front.colon(), !shortFront);
		if (connector == null) {
			return -1;
		}
		int backStart = connector.backStart();
		TimeMatch back = timeAt(backStart);
		if (back == null || !closes(connector, back.end()) || (back.colon() && continuesDigitRun(back.end()))) {
			return -1;
		}
		boolean shapeMatches = shortFront
			? back.meridiem() == null && !back.colon()
			: !connector.hyphen() || back.colon();
		if (!shapeMatches) {
			return -1;
		}
		TimeMatch inherited = back.inherit(front);
		if (inherited.valid()) {
			if (front.valid()) {
				found.add(new Found(frontText, front.toValue()));
			}
			String backText = text.substring(backStart, back.end());
			if (back.meridiem() == null && inherited.meridiem() != null) {
				backText = inherited.meridiem() + " " + backText;
			}
			found.add(new Found(backText, inherited.toValue()));
		}
		return back.end();
	}

	private static boolean validTime(int hour, int minute) {
		return hour <= 24 && minute <= 59 && !(hour == 24 && minute > 0);
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
	 * 2~3일, 10~15만 원, 2천~3천만 원, 3~5시, 3-5일: 앞쪽 값에 뒤쪽 자릿수·단위를 붙여 둘 다 뽑는다.
	 * 앞쪽이 숫자 하나면 뒤쪽 자릿수를 모두 붙이고(2~3천만 → 2천만), 천·백이 붙어 있으면 뒤쪽의 만·억만 붙인다.
	 * 앞쪽의 천·백이 뒤쪽 것보다 크면(2천~3만) 자릿수는 붙이지 않고 단위만 붙인다.
	 * 하이픈은 앞쪽이 세 자리 이하 숫자이고 뒤쪽이 세 자리 이하 숫자 바로 뒤 단위로 끝날 때만 범위로 본다 (전화번호와 구분).
	 */
	private int range(int i) {
		Amount front = amount(i);
		if (front.large() || !front.simple()) {
			return -1;
		}
		boolean shortNumber = digitsEnd(i) == front.end() && front.end() - i <= 3;
		Connector connector = connectorAt(front.end(), shortNumber, false);
		if (connector == null) {
			return -1;
		}
		int backStart = connector.backStart();
		boolean hyphen = connector.hyphen();
		int backDigitsEnd = digitsEnd(backStart);
		if (backDigitsEnd == backStart || (hyphen && backDigitsEnd - backStart > 3)) {
			return -1;
		}
		if (isHourMark(backDigitsEnd)) {
			if (!shortNumber || front.end() - i > 2) {
				return -1;
			}
			TimeMatch frontTime = new TimeMatch(null, parse(i, front.end()), 0, false, front.end());
			return timeRange(i, frontTime, text.substring(i, front.end()) + "시", true);
		}
		QuantityMatch back = quantityMatch(backStart);
		if (back == null || !back.simple() || (hyphen && isSpace(back.number().numberEnd()))) {
			return -1;
		}
		Amount backNumber = back.number();
		BigDecimal factor;
		int suffixStart;
		if (front.scale().compareTo(BigDecimal.ONE) == 0) {
			factor = backNumber.scale();
			suffixStart = backNumber.numberEnd();
		} else if (front.scale().compareTo(backNumber.scale().divide(backNumber.largeScale())) <= 0) {
			factor = backNumber.largeScale();
			suffixStart = backNumber.largeStart();
		} else {
			factor = BigDecimal.ONE;
			suffixStart = backNumber.end();
		}
		String frontText = text.substring(i, front.end()) + text.substring(suffixStart, back.end());
		BigDecimal frontValue = front.value().multiply(factor).multiply(back.multiplier());
		found.add(new Found(frontText, new Quantity(back.unit(), frontValue)));
		add(backStart, back.end(), new Quantity(back.unit(), back.amount()));
		return back.end();
	}

	/**
	 * 앞쪽 값 끝(frontEnd) 다음의 범위 기호. 물결은 앞뒤 공백을 허용하고, 하이픈은 hyphenAllowed일 때 붙여 쓴 것만 본다.
	 * untilAllowed면 앞쪽에 바로 붙은 부터·에서도 보며, 그 뒤에는 공백만 건너뛴다. 범위가 아니면 null
	 */
	private Connector connectorAt(int frontEnd, boolean hyphenAllowed, boolean untilAllowed) {
		int separator = skipSpaces(frontEnd);
		if (TILDES.indexOf(charAt(separator)) >= 0) {
			return new Connector(skipSpaces(separator + 1), false, false);
		}
		if (hyphenAllowed && isHyphen(frontEnd)) {
			return new Connector(frontEnd + 1, true, false);
		}
		if (untilAllowed) {
			for (String word : FROM) {
				if (text.startsWith(word, frontEnd)) {
					return new Connector(skipSpaces(frontEnd + word.length()), false, true);
				}
			}
		}
		return null;
	}

	/** 부터…까지로 연 범위는 뒤쪽 값 바로 뒤에 까지가 와야 닫힌다 */
	private boolean closes(Connector connector, int backEnd) {
		return !connector.until() || text.startsWith(UNTIL, backEnd);
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
				? new QuantityMatch(null, amount.value(), BigDecimal.ONE, amount, amount.simple(), amount.end())
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
		return new QuantityMatch(unit.canonical(), value, multiplier, amount, simple, end);
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
		BigDecimal lastLargeScale;
		int largeStart;
		boolean large = false;
		int terms = 0;
		int end;
		while (true) {
			terms++;
			lastLargeScale = BigDecimal.ONE;
			largeStart = -1;
			int scaleAt = skipSpaces(num.end());
			BigDecimal smallScale = smallScaleAt(scaleAt);
			BigDecimal largeScale;
			if (smallScale != null) {
				small = small.add(num.value().multiply(smallScale));
				scale = smallScale;
				end = scaleAt + 1;
				largeScale = largeScaleAt(end);
				if (largeScale != null) {
					largeStart = end;
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
				largeStart = scaleAt;
				end = scaleAt + 1;
			}
			if (largeScale != null) {
				total = total.add(small.multiply(largeScale));
				small = BigDecimal.ZERO;
				scale = scale.multiply(largeScale);
				lastLargeScale = largeScale;
				large = true;
			}
			int next = skipSpaces(end);
			if (!isDigit(next) || (next > end && !scaledNumberAt(next))) {
				break;
			}
			num = number(next);
		}
		return new Amount(total.add(small), scale, lastLargeScale, large, terms == 1, numberEnd,
			largeStart < 0 ? end : largeStart, end);
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

	/** 시 숫자 뒤에 ":분 두 자리"가 오는지 */
	private boolean isColonMinute(int hourEnd) {
		return charAt(hourEnd) == ':' && digitsEnd(hourEnd + 1) == hourEnd + 3;
	}

	private boolean isHyphen(int i) {
		return HYPHENS.indexOf(charAt(i)) >= 0;
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
