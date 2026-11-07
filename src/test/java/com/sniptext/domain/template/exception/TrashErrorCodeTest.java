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

	@Test
	@DisplayName("휴지통에 없는 템플릿은 명세의 결과 코드와 문구다")
	void trashedTemplateNotFound() {
		assertThat(TrashErrorCode.TRASHED_TEMPLATE_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(TrashErrorCode.TRASHED_TEMPLATE_NOT_FOUND.getCode()).isEqualTo("TRASH404_1");
		assertThat(TrashErrorCode.TRASHED_TEMPLATE_NOT_FOUND.getMessage()).isEqualTo("휴지통에서 템플릿을 찾을 수 없어요.");
	}

}
