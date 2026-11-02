package com.sniptext.domain.auth.entity;

/** 재발급 토큰을 받은 곳. 로그아웃은 둘 다 폐기한다 (명세 4번). 확장 전용 발급 방식은 S2에서 정한다. */
public enum ClientType {

	WEB,

	EXTENSION

}
