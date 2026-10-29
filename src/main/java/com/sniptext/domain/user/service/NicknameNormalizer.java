package com.sniptext.domain.user.service;

import java.text.Normalizer;

import com.sniptext.domain.user.entity.User;

/**
 * 구글 이름으로 닉네임을 만든다 (명세 2번 비고). 앞뒤 공백을 지우고, 50자가 넘으면 자르고, 비면 이메일 앞부분을 쓴다.
 * 글자 수는 NFC 정규화한 뒤 세고, 공백은 화면(JS trim)과 같은 범위로 본다 (명세 공통 규칙 5장, NBSP·BOM 포함).
 * 템플릿의 같은 규칙은 template 도메인 안에 있어 쓸 수 없다.
 * 50자로 자를 때 이모지 같은 두 칸짜리 글자의 앞 반쪽만 남지 않게 한 칸 덜 자른다.
 */
final class NicknameNormalizer {

	private static final char BYTE_ORDER_MARK = '﻿';

	private NicknameNormalizer() {
	}

	static String normalize(String name, String email) {
		String nickname = name == null ? "" : strip(Normalizer.normalize(name, Normalizer.Form.NFC));
		if (nickname.isEmpty()) {
			nickname = strip(localPart(email));
		}
		// 자른 끝이 공백이면 다시 지운다 (앞이 공백이 아니므로 비지 않는다)
		return strip(truncate(nickname));
	}

	private static String localPart(String email) {
		int at = email.indexOf('@');
		return at < 0 ? email : email.substring(0, at);
	}

	private static String truncate(String text) {
		int max = User.NICKNAME_MAX_LENGTH;
		if (text.length() <= max) {
			return text;
		}
		int end = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
		return text.substring(0, end);
	}

	private static String strip(String text) {
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

	private static boolean isSpace(char ch) {
		return Character.isWhitespace(ch) || Character.isSpaceChar(ch) || ch == BYTE_ORDER_MARK;
	}

}
