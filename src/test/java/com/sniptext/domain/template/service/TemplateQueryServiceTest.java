package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.query.TemplateDetailQuery;
import com.sniptext.domain.template.service.dto.result.TemplateDetailResult;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class TemplateQueryServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long TEMPLATE_ID = 12L;
	private static final TemplateDetailQuery QUERY = new TemplateDetailQuery(USER_ID, TEMPLATE_ID);

	@Mock
	private TemplateRepository templateRepository;

	@InjectMocks
	private TemplateQueryService templateQueryService;

	@Test
	@DisplayName("보유 개수는 휴지통을 제외한 개수다")
	void countActive() {
		given(templateRepository.countByUserIdAndDeletedAtIsNull(1L)).willReturn(3L);

		assertThat(templateQueryService.countActive(1L)).isEqualTo(3L);
	}

	@Test
	@DisplayName("상세 조회는 본문에서 뽑은 변수 목록을 함께 돌려준다")
	void getTemplate() {
		given(templateRepository.findByIdAndUserId(TEMPLATE_ID, USER_ID))
			.willReturn(Optional.of(TemplateFixture.template(USER_ID)));

		TemplateDetailResult result = templateQueryService.getTemplate(QUERY);

		assertThat(result.title()).isEqualTo(TemplateFixture.TITLE);
		assertThat(result.shortcut()).isEqualTo(TemplateFixture.SHORTCUT);
		assertThat(result.content()).isEqualTo(TemplateFixture.CONTENT);
		assertThat(result.variables()).containsExactly("고객명");
	}

	@Test
	@DisplayName("본인 소유로 찾지 못하면 TEMPLATE404_1")
	void getTemplateNotFound() {
		given(templateRepository.findByIdAndUserId(TEMPLATE_ID, USER_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> templateQueryService.getTemplate(QUERY))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(TemplateErrorCode.TEMPLATE_NOT_FOUND);
	}

	@Test
	@DisplayName("내 템플릿이 휴지통에 있으면 TEMPLATE404_2")
	void getTemplateInTrash() {
		Template trashed = TemplateFixture.template(USER_ID);
		trashed.moveToTrash(LocalDateTime.of(2026, 10, 7, 10, 30));
		given(templateRepository.findByIdAndUserId(TEMPLATE_ID, USER_ID)).willReturn(Optional.of(trashed));

		assertThatThrownBy(() -> templateQueryService.getTemplate(QUERY))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(TemplateErrorCode.TEMPLATE_IN_TRASH);
	}

}
