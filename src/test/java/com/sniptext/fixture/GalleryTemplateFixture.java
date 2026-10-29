package com.sniptext.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.gallery.entity.GalleryTemplate;

/** 테스트용 갤러리 템플릿. 생성 인자가 바뀌면 여기만 고친다. */
public final class GalleryTemplateFixture {

	public static final String TITLE = "프리랜서 견적 안내";
	public static final String SHORTCUT = "/견적";
	public static final String CONTENT = "안녕하세요 {{고객명}}님,\n요청하신 작업의 견적을 안내드립니다.\n"
		+ "- 작업 범위: {{작업범위}}\n- 견적 금액: {{금액}}원\n- 작업 기간: {{기간}}\n{{고객명}}님 감사합니다.";

	private GalleryTemplateFixture() {
	}

	public static GalleryTemplate published(int displayOrder) {
		return GalleryTemplate.create(GalleryCategory.WORK, TITLE, SHORTCUT, CONTENT, displayOrder, true);
	}

	public static GalleryTemplate unpublished(int displayOrder) {
		return GalleryTemplate.create(GalleryCategory.WORK, TITLE, SHORTCUT, CONTENT, displayOrder, false);
	}

	public static GalleryTemplate galleryTemplate(GalleryCategory category, String title, String shortcut,
		String content, int displayOrder, boolean published) {
		return GalleryTemplate.create(category, title, shortcut, content, displayOrder, published);
	}

	/** 저장하지 않는 단위 테스트에서 ID가 필요할 때 */
	public static GalleryTemplate withId(GalleryTemplate galleryTemplate, Long id) {
		ReflectionTestUtils.setField(galleryTemplate, "id", id);
		return galleryTemplate;
	}

}
