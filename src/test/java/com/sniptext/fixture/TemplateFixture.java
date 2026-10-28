package com.sniptext.fixture;

import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.entity.TemplateContent;
import com.sniptext.domain.template.entity.TemplateTitle;

/** 테스트용 내 템플릿. 생성 인자가 바뀌면 여기만 고친다. */
public final class TemplateFixture {

	public static final String TITLE = "견적 안내";
	public static final String SHORTCUT = "/견적";
	public static final String CONTENT = "안녕하세요 {{고객명}}님,\n요청하신 견적을 보내드립니다.";

	private TemplateFixture() {
	}

	public static Template template(Long userId) {
		return template(userId, SHORTCUT);
	}

	public static Template template(Long userId, String shortcut) {
		return Template.create(userId, TemplateTitle.create(TITLE), Shortcut.create(shortcut),
			TemplateContent.create(CONTENT));
	}

	public static Template templateFromGallery(Long userId, Long sourceGalleryTemplateId) {
		return Template.createFromGallery(userId, sourceGalleryTemplateId, TemplateTitle.create(TITLE),
			Shortcut.create(SHORTCUT), TemplateContent.create(CONTENT));
	}

}
