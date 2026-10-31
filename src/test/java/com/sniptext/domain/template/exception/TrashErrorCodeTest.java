package com.sniptext.domain.template.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TrashErrorCodeTest {

	@Test
	@DisplayName("잘못된 페이지 요청은 명세의 결과 코드와 문구다")
	void invalidPageRequest() {
		assertThat(TrashErrorCode.INVALID_PAGE_REQUEST.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(TrashErrorCode.INVALID_PAGE_REQUEST.getCode()).isEqualTo("TRASH400_1");
		assertThat(TrashErrorCode.INVALID_PAGE_REQUEST.getMessage()).isEqualTo("잘못된 페이지 요청이에요.");
	}

}
