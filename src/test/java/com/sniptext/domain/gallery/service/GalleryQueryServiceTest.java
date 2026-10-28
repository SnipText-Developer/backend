package com.sniptext.domain.gallery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.gallery.exception.GalleryErrorCode;
import com.sniptext.domain.gallery.repository.GalleryTemplateRepository;
import com.sniptext.domain.gallery.service.dto.result.GalleryTemplateListResult;
import com.sniptext.domain.gallery.service.dto.result.GalleryTemplateResult;
import com.sniptext.fixture.GalleryTemplateFixture;
import com.sniptext.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class GalleryQueryServiceTest {

	@Mock
	private GalleryTemplateRepository galleryTemplateRepository;

	@InjectMocks
	private GalleryQueryService galleryQueryService;

	@Test
	@DisplayName("목록은 레포지토리 순서 그대로, 개수와 본문에서 뽑은 변수를 담는다")
	void getGalleryTemplates() {
		given(galleryTemplateRepository.findAllByPublishedTrueOrderByDisplayOrderAscIdAsc()).willReturn(List.of(
			GalleryTemplateFixture.withId(GalleryTemplateFixture.published(10), 3L),
			GalleryTemplateFixture.withId(GalleryTemplateFixture.galleryTemplate(GalleryCategory.PERSONAL,
				"감사 인사", "/감사", "변수 없는 본문 {{형식 틀림}}", 20, true), 1L)));

		GalleryTemplateListResult result = galleryQueryService.getGalleryTemplates();

		assertThat(result.totalCount()).isEqualTo(2);
		assertThat(result.templates()).extracting(GalleryTemplateResult::galleryTemplateId).containsExactly(3L, 1L);
		assertThat(result.templates().get(0).variables()).containsExactly("고객명", "작업범위", "금액", "기간");
		assertThat(result.templates().get(1).variables()).isEmpty();
	}

	@Test
	@DisplayName("공개 템플릿이 없으면 개수 0, 빈 목록이다")
	void emptyGallery() {
		given(galleryTemplateRepository.findAllByPublishedTrueOrderByDisplayOrderAscIdAsc()).willReturn(List.of());

		GalleryTemplateListResult result = galleryQueryService.getGalleryTemplates();

		assertThat(result.totalCount()).isZero();
		assertThat(result.templates()).isEmpty();
	}

	@Test
	@DisplayName("상세는 모든 값과 첫 등장 순·중복 제거한 변수를 담는다")
	void getGalleryTemplate() {
		given(galleryTemplateRepository.findByIdAndPublishedTrue(3L))
			.willReturn(Optional.of(GalleryTemplateFixture.withId(GalleryTemplateFixture.published(10), 3L)));

		GalleryTemplateResult result = galleryQueryService.getGalleryTemplate(3L);

		assertThat(result).isEqualTo(new GalleryTemplateResult(3L, GalleryCategory.WORK, GalleryTemplateFixture.TITLE,
			GalleryTemplateFixture.SHORTCUT, GalleryTemplateFixture.CONTENT, List.of("고객명", "작업범위", "금액", "기간")));
	}

	@Test
	@DisplayName("없거나 비공개면 GALLERY404_1")
	void notFound() {
		given(galleryTemplateRepository.findByIdAndPublishedTrue(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> galleryQueryService.getGalleryTemplate(99L))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(GalleryErrorCode.GALLERY_TEMPLATE_NOT_FOUND);
	}

}
