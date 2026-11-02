package com.sniptext.domain.ai.support;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 핵심 정보의 정규형. 표기가 달라도 정규형이 같으면 같은 값이다.
 * covers는 "같거나 더 구체적이면서 어긋나지 않는다"를 판정한다 (10월 14일은 10월을 덮고, 그 반대는 아니다).
 */
sealed interface KeyInfoValue {

	boolean covers(KeyInfoValue other);

	/** 단위가 붙은 수. unit이 null이면 단위 없는 큰 수(300만)이고, 값이 같으면 어떤 단위와도 서로 같다. */
	record Quantity(String unit, BigDecimal amount) implements KeyInfoValue {

		public Quantity {
			amount = amount.stripTrailingZeros();
		}

		@Override
		public boolean covers(KeyInfoValue other) {
			return other instanceof Quantity quantity
				&& amount.compareTo(quantity.amount) == 0
				&& (unit == null || quantity.unit == null || unit.equals(quantity.unit));
		}

	}

	/** 연도·일은 없을 수 있다. 빈칸을 채운 쪽이 더 구체적이다. */
	record DateValue(Integer year, int month, Integer day) implements KeyInfoValue {

		@Override
		public boolean covers(KeyInfoValue other) {
			return other instanceof DateValue date
				&& month == date.month
				&& (date.year == null || date.year.equals(year))
				&& (date.day == null || date.day.equals(day));
		}

	}

	/** hours는 24시간제 후보. 오전·오후가 없는 3시는 {3, 15}이고, 후보가 적은 쪽이 더 구체적이다. */
	record TimeValue(Set<Integer> hours, int minute) implements KeyInfoValue {

		public TimeValue {
			hours = Set.copyOf(hours);
		}

		@Override
		public boolean covers(KeyInfoValue other) {
			return other instanceof TimeValue time
				&& minute == time.minute
				&& time.hours.containsAll(hours);
		}

	}

}
