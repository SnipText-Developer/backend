package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.template.repository.TemplateRepository;

@ExtendWith(MockitoExtension.class)
class TemplateQueryServiceTest {

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

}
