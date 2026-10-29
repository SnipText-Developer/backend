package com.sniptext.global.crypto;

/**
 * 템플릿 본문 같은 개인 데이터를 DB에 넣기 전에 암호화한다. 저장값에는 형식 버전이 붙는다 (예: {@code v1:...}).
 * 나중에 KMS 봉투 암호화로 바꿀 때 이 인터페이스의 구현만 교체한다.
 */
public interface ContentCipher {

	String encrypt(String plaintext);

	/** 키가 다르거나 변조됐거나 형식이 틀리면 조용히 넘기지 않고 ContentDecryptionException을 던진다. */
	String decrypt(String stored);

}
