package com.sniptext.domain.template.support;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 휴지통 목록의 다음 페이지 위치. 이 페이지 마지막 항목의 (삭제 시각, ID)를 이어 붙여 Base64 URL로 인코딩한다.
 * 화면은 값을 해석하지 않고 nextCursor를 그대로 돌려보낸다.
 * 같은 초 안에서도 순서가 갈리므로 시각은 DATETIME(6)과 같은 마이크로초까지 넣는다.
 * 해석할 때 자릿수를 고정해 DB가 받지 못하는 연도(+12026)가 쿼리까지 가지 않게 한다.
 */
public record TrashCursor(LocalDateTime deletedAt, Long id) {

	private static final String SEPARATOR = "_";
	private static final DateTimeFormatter DELETED_AT_FORMAT = DateTimeFormatter.ofPattern(
		"uuuu-MM-dd'T'HH:mm:ss.SSSSSS");
	private static final Pattern FORMAT = Pattern.compile(
		"(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6})" + SEPARATOR + "(\\d{1,19})");

	public String encode() {
		String raw = DELETED_AT_FORMAT.format(deletedAt) + SEPARATOR + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/** 서버가 만든 형식이 아니면 비어 있다. 어떤 에러로 응답할지는 서비스가 정한다 */
	public static Optional<TrashCursor> decode(String cursor) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			Matcher matcher = FORMAT.matcher(raw);
			if (!matcher.matches()) {
				return Optional.empty();
			}
			return Optional.of(new TrashCursor(LocalDateTime.parse(matcher.group(1)), Long.valueOf(matcher.group(2))));
		} catch (IllegalArgumentException | DateTimeParseException exception) {
			return Optional.empty();
		}
	}

}
