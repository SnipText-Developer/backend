package com.sniptext.global.crypto;

/**
 * 저장된 본문을 복호화하지 못했다 (다른 키, 변조, 형식 오류, 모르는 버전).
 * 사용자 입력 문제가 아니라 키 설정 실수나 데이터 손상이라 응답은 전역 예외 처리의 500 그대로다.
 * 로그·알림에서 다른 500과 구분하려고 따로 둔다. 메시지에 평문·저장값·키를 넣지 않는다.
 * JPA 변환기에서 던지면 Hibernate가 PersistenceException으로 감싸므로 원인 사슬 안에 들어 있다.
 */
public class ContentDecryptionException extends RuntimeException {

	public ContentDecryptionException(String message) {
		super(message);
	}

	public ContentDecryptionException(String message, Throwable cause) {
		super(message, cause);
	}

}
