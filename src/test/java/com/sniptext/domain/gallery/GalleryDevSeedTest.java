package com.sniptext.domain.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.TemplateContent;
import com.sniptext.domain.template.entity.TemplateTitle;
import com.sniptext.support.IntegrationTestSupport;

/**
 * 개발용 갤러리 시드(db/seed/R__dev_seed_gallery.sql)가 내 템플릿 규칙을 지키는지 본다.
 * 갤러리에서 내 템플릿으로 저장할 때 같은 규칙을 거치므로, 어긋난 시드는 저장 단계에서 500이 된다.
 * 운영 데이터를 마이그레이션으로 넣을 때도 같은 방식으로 검사한다.
 */
@ActiveProfiles("local")
class GalleryDevSeedTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("시드 행이 모두 단축어·이름·본문 규칙을 통과한다")
	void seedFollowsTemplateRules() {
		List<Map<String, Object>> rows = seedRows();

		assertThat(rows).isNotEmpty().allSatisfy(row -> {
			String id = String.valueOf(row.get("id"));
			String shortcut = (String)row.get("shortcut");
			String title = (String)row.get("title");
			String content = (String)row.get("content");
			assertThat(Shortcut.findViolation(shortcut)).as("%s번 단축어", id).isEmpty();
			assertThat(TemplateTitle.findViolation(title)).as("%s번 이름", id).isEmpty();
			assertThat(TemplateContent.findViolation(content)).as("%s번 본문", id).isEmpty();
			// 저장 규칙을 거친 값과 같아야 내 템플릿으로 저장해도 바뀌지 않는다 (NFC, 앞뒤 공백, CRLF)
			assertThat(Shortcut.create(shortcut).getValue()).as("%s번 단축어 정규화", id).isEqualTo(shortcut);
			assertThat(TemplateTitle.create(title).getValue()).as("%s번 이름 정규화", id).isEqualTo(title);
			assertThat(TemplateContent.create(content).getValue()).as("%s번 본문 정규화", id).isEqualTo(content);
			assertThat(GalleryCategory.valueOf((String)row.get("category"))).isNotNull();
		});
	}

	@Test
	@DisplayName("카테고리마다 공개 2개씩 8개, 비공개 1개이고 노출 순서와 단축어가 겹치지 않는다")
	void seedComposition() {
		List<Map<String, Object>> rows = seedRows();

		List<Map<String, Object>> published = rows.stream().filter(row -> isPublished(row)).toList();
		assertThat(published).hasSize(8);
		assertThat(rows).hasSize(9);
		for (GalleryCategory category : GalleryCategory.values()) {
			assertThat(published).filteredOn(row -> category.name().equals(row.get("category")))
				.as(category.name()).hasSize(2);
		}
		assertThat(rows).extracting(row -> row.get("display_order")).doesNotHaveDuplicates();
		assertThat(rows).extracting(row -> Shortcut.create((String)row.get("shortcut")).getKey())
			.doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("변수가 있는 템플릿과 없는 템플릿이 섞여 있다")
	void seedHasVariablesAndPlainContent() {
		List<String> contents = seedRows().stream().map(row -> (String)row.get("content")).toList();

		assertThat(contents).anyMatch(content -> content.contains("{{"));
		assertThat(contents).anyMatch(content -> !content.contains("{{"));
	}

	private List<Map<String, Object>> seedRows() {
		return jdbcTemplate.queryForList(
			"SELECT id, category, title, shortcut, content, display_order, is_published FROM gallery_templates ORDER BY id");
	}

	private static boolean isPublished(Map<String, Object> row) {
		Object value = row.get("is_published");
		return value instanceof Boolean bool ? bool : ((Number)value).intValue() == 1;
	}

}
