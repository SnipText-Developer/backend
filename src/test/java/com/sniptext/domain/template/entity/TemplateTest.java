package com.sniptext.domain.template.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.sniptext.fixture.TemplateFixture;

/**
 * 이름·본문 자체의 규칙은 TemplateTitleTest·TemplateContentTest에서 본다.
 * 생성 시 수정 시각은 저장할 때 채워지므로 TemplateRepositoryTest에서 본다.
 */
class TemplateTest {

	private static final Long USER_ID = 1L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 10, 0);
	private static final TemplateTitle TITLE = TemplateTitle.create(TemplateFixture.TITLE);
	private static final Shortcut SHORTCUT = Shortcut.create(TemplateFixture.SHORTCUT);
	private static final TemplateContent CONTENT = TemplateContent.create(TemplateFixture.CONTENT);

	@Nested
	@DisplayName("생성")
	class Create {

		@Test
		@DisplayName("직접 만들면 사용·휴지통 시각과 갤러리 원본이 없다")
		void create() {
			Template template = Template.create(USER_ID, TITLE, SHORTCUT, CONTENT);

			assertThat(template.getUserId()).isEqualTo(USER_ID);
			assertThat(template.getTitle()).isEqualTo(TITLE);
			assertThat(template.getShortcut()).isEqualTo(SHORTCUT);
			assertThat(template.getContent()).isEqualTo(CONTENT);
			assertThat(template.getLastUsedAt()).isNull();
			assertThat(template.getDeletedAt()).isNull();
			assertThat(template.getSourceGalleryTemplateId()).isNull();
			assertThat(template.isInTrash()).isFalse();
		}

		@Test
		@DisplayName("갤러리에서 만들면 원본 갤러리 템플릿 ID를 기록한다")
		void createFromGallery() {
			Template template = Template.createFromGallery(USER_ID, 7L, TITLE, SHORTCUT, CONTENT);

			assertThat(template.getSourceGalleryTemplateId()).isEqualTo(7L);
			assertThat(template.getUserId()).isEqualTo(USER_ID);
			assertThat(template.getLastUsedAt()).isNull();
			assertThat(template.getDeletedAt()).isNull();
		}

		@Test
		@DisplayName("회원 ID, 이름, 단축어, 본문 중 하나라도 없으면 IllegalArgumentException")
		void requiredValues() {
			assertThatThrownBy(() -> Template.create(null, TITLE, SHORTCUT, CONTENT))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Template.create(USER_ID, null, SHORTCUT, CONTENT))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Template.create(USER_ID, TITLE, null, CONTENT))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> Template.create(USER_ID, TITLE, SHORTCUT, null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("갤러리에서 만들 때 원본 ID가 없으면 IllegalArgumentException")
		void galleryIdRequired() {
			assertThatThrownBy(() -> Template.createFromGallery(USER_ID, null, TITLE, SHORTCUT, CONTENT))
				.isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	@DisplayName("수정")
	class Edit {

		@Test
		@DisplayName("이름·단축어·본문과 수정 시각만 바뀌고 사용·휴지통 시각은 그대로다")
		void edit() {
			Template template = editedTemplate();
			template.markUsed(NOW.plusHours(1));
			TemplateTitle newTitle = TemplateTitle.create("새 이름");
			Shortcut newShortcut = Shortcut.create("/Quote");
			TemplateContent newContent = TemplateContent.create("새 본문");

			template.edit(newTitle, newShortcut, newContent, NOW.plusHours(2));

			assertThat(template.getTitle()).isEqualTo(newTitle);
			assertThat(template.getShortcut()).isEqualTo(newShortcut);
			assertThat(template.getContent()).isEqualTo(newContent);
			assertThat(template.getEditedAt()).isEqualTo(NOW.plusHours(2));
			assertThat(template.getLastUsedAt()).isEqualTo(NOW.plusHours(1));
			assertThat(template.getDeletedAt()).isNull();
		}

		@Test
		@DisplayName("이름, 단축어, 본문, 수정 시각 중 하나라도 없으면 IllegalArgumentException")
		void requiredValues() {
			Template template = editedTemplate();

			assertThatThrownBy(() -> template.edit(null, SHORTCUT, CONTENT, NOW))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> template.edit(TITLE, null, CONTENT, NOW))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> template.edit(TITLE, SHORTCUT, null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> template.edit(TITLE, SHORTCUT, CONTENT, null))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("불변 조건에 걸리면 아무 값도 바뀌지 않는다")
		void noPartialUpdate() {
			Template template = editedTemplate();

			assertThatThrownBy(() -> template.edit(TemplateTitle.create("새 이름"), Shortcut.create("/Quote"), null,
				NOW.plusHours(1)))
				.isInstanceOf(IllegalArgumentException.class);

			assertThat(template.getTitle()).isEqualTo(TITLE);
			assertThat(template.getShortcut()).isEqualTo(SHORTCUT);
			assertThat(template.getContent()).isEqualTo(CONTENT);
			assertThat(template.getEditedAt()).isEqualTo(NOW);
		}

	}

	@Nested
	@DisplayName("사용 기록")
	class MarkUsed {

		@Test
		@DisplayName("최근 사용 시각만 바뀌고 수정 시각은 그대로다")
		void markUsed() {
			Template template = editedTemplate();

			template.markUsed(NOW.plusHours(1));

			assertThat(template.getLastUsedAt()).isEqualTo(NOW.plusHours(1));
			assertThat(template.getEditedAt()).isEqualTo(NOW);
			assertThat(template.getDeletedAt()).isNull();
		}

		@Test
		@DisplayName("시각이 없으면 IllegalArgumentException")
		void nowRequired() {
			Template template = editedTemplate();

			assertThatThrownBy(() -> template.markUsed(null)).isInstanceOf(IllegalArgumentException.class);
		}

	}

	@Nested
	@DisplayName("휴지통")
	class Trash {

		@Test
		@DisplayName("휴지통으로 옮기면 휴지통 시각만 바뀌고 수정·사용 시각은 그대로다")
		void moveToTrash() {
			Template template = editedTemplate();
			template.markUsed(NOW.plusHours(1));

			template.moveToTrash(NOW.plusHours(2));

			assertThat(template.isInTrash()).isTrue();
			assertThat(template.getDeletedAt()).isEqualTo(NOW.plusHours(2));
			assertThat(template.getEditedAt()).isEqualTo(NOW);
			assertThat(template.getLastUsedAt()).isEqualTo(NOW.plusHours(1));
		}

		@Test
		@DisplayName("휴지통으로 옮길 시각이 없으면 IllegalArgumentException")
		void nowRequired() {
			Template template = editedTemplate();

			assertThatThrownBy(() -> template.moveToTrash(null)).isInstanceOf(IllegalArgumentException.class);
		}

	}

	/** 수정 시각이 NOW인 템플릿 */
	private static Template editedTemplate() {
		Template template = TemplateFixture.template(USER_ID);
		template.edit(TITLE, SHORTCUT, CONTENT, NOW);
		return template;
	}

}
