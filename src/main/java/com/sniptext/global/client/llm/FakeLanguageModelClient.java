package com.sniptext.global.client.llm;

import java.util.regex.Pattern;

/**
 * 로컬·테스트용 가짜 언어 모델. 외부를 부르지 않고 사용자 메시지의 원문에 표시만 붙여 돌려준다.
 * 사용자 메시지가 &lt;text&gt;...&lt;/text&gt;로 감싸져 있으면 태그와 그 안쪽 앞뒤 줄바꿈을 벗긴 원문을 쓴다.
 * 원문을 로그에 남기지 않는다.
 */
public class FakeLanguageModelClient implements LanguageModelClient {

	static final String PREFIX = "[가짜 교정] ";

	private static final String OPEN_TAG = "<text>";
	private static final String CLOSE_TAG = "</text>";
	private static final Pattern EDGE_LINE_BREAKS = Pattern.compile("^\\R+|\\R+$");

	@Override
	public LanguageModelResult complete(LanguageModelRequest request) {
		return new LanguageModelResult(PREFIX + unwrap(request.userText()), false);
	}

	private static String unwrap(String userText) {
		if (userText.length() < OPEN_TAG.length() + CLOSE_TAG.length()
			|| !userText.startsWith(OPEN_TAG) || !userText.endsWith(CLOSE_TAG)) {
			return userText;
		}
		String inner = userText.substring(OPEN_TAG.length(), userText.length() - CLOSE_TAG.length());
		return EDGE_LINE_BREAKS.matcher(inner).replaceAll("");
	}

}
