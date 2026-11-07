package com.sniptext.domain.gallery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.gallery.exception.GalleryErrorCode;
import com.sniptext.domain.gallery.service.dto.command.GallerySaveCommand;
import com.sniptext.domain.gallery.service.dto.result.GallerySaveResult;
import com.sniptext.domain.gallery.service.dto.result.GalleryTemplateResult;
import com.sniptext.domain.template.service.TemplateCommandService;
import com.sniptext.domain.template.service.dto.command.TemplateCreateFromGalleryCommand;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAssignResult;
import com.sniptext.global.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class GalleryCommandServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long GALLERY_TEMPLATE_ID = 3L;

	@Mock
	private GalleryQueryService galleryQueryService;

	@Mock
	private TemplateCommandService templateCommandService;

	@InjectMocks
	private GalleryCommandService galleryCommandService;

	@Test
	@DisplayName("원본의 제목·단축어·본문을 그대로 넘기고, 저장 결과를 그대로 옮겨 담는다")
	void saveToMyTemplates() {
		given(galleryQueryService.getGalleryTemplate(GALLERY_TEMPLATE_ID)).willReturn(new GalleryTemplateResult(
			GALLERY_TEMPLATE_ID, GalleryCategory.WORK, "견적 안내", "/견적", "본문 {{고객명}}", List.of("고객명")));
		given(templateCommandService.createFromGallery(any()))
			.willReturn(new TemplateShortcutAssignResult(21L, "견적 안내", "/견적2", "/견적", true));

		GallerySaveResult result = galleryCommandService.saveToMyTemplates(
			new GallerySaveCommand(USER_ID, GALLERY_TEMPLATE_ID));

		then(templateCommandService).should().createFromGallery(
			new TemplateCreateFromGalleryCommand(USER_ID, GALLERY_TEMPLATE_ID, "견적 안내", "/견적", "본문 {{고객명}}"));
		assertThat(result).isEqualTo(new GallerySaveResult(21L, "견적 안내", "/견적2", "/견적", true));
	}

	@Test
	@DisplayName("없거나 비공개면 GALLERY404_1이고 내 템플릿 저장을 부르지 않는다")
	void notFound() {
		given(galleryQueryService.getGalleryTemplate(GALLERY_TEMPLATE_ID))
			.willThrow(new BusinessException(GalleryErrorCode.GALLERY_TEMPLATE_NOT_FOUND));

		assertThatThrownBy(() -> galleryCommandService.saveToMyTemplates(
			new GallerySaveCommand(USER_ID, GALLERY_TEMPLATE_ID)))
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(GalleryErrorCode.GALLERY_TEMPLATE_NOT_FOUND);

		then(templateCommandService).shouldHaveNoInteractions();
	}

}
