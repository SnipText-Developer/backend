package com.sniptext.domain.template.entity;

/**
 * 화면(JS trim)과 같은 기준의 공백 판단. 자바 isWhitespace·strip은 NBSP(U+00A0)·BOM(U+FEFF) 등을 공백으로 보지 않아,
 * 화면에서는 비었다고 막는 입력을 서버가 통과시키게 된다. 그래서 유니코드 공백 문자(Zs·Zl·Zp)와 BOM을 더한다.
 * 이름·본문 값 객체만 쓰므로 패키지 밖에 공개하지 않는다.
 */
final class TextWhitespace {

	private static final char BYTE_ORDER_MARK = '﻿';

	private TextWhitespace() {
	}

	static boolean isBlank(String text) {
		return text.chars().allMatch(TextWhitespace::isSpace);
	}

	static String strip(String text) {
		int start = 0;
		int end = text.length();
		while (start < end && isSpace(text.charAt(start))) {
			start++;
		}
		while (end > start && isSpace(text.charAt(end - 1))) {
			end--;
		}
		return text.substring(start, end);
	}

	private static boolean isSpace(int ch) {
		return Character.isWhitespace(ch) || Character.isSpaceChar(ch) || ch == BYTE_ORDER_MARK;
	}

}
