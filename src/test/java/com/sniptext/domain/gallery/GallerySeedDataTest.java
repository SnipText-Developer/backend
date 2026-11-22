package com.sniptext.domain.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.template.entity.Shortcut;
import com.sniptext.domain.template.entity.TemplateContent;
import com.sniptext.domain.template.entity.TemplateTitle;
import com.sniptext.domain.template.support.TemplateVariableExtractor;
import com.sniptext.support.IntegrationTestSupport;

/**
 * 갤러리 데이터 마이그레이션(V202611222140__replace_gallery_templates_for_ut.sql)이 내 템플릿 규칙을 지키는지 본다.
 * 갤러리에서 내 템플릿으로 저장할 때 같은 규칙을 거치므로, 어긋난 데이터는 저장 단계에서 500이 된다.
 * 운영 서버와 같이 개발용 시드 경로(db/seed)까지 읽는 local 프로필로 띄워, 비워 둔 시드가 데이터를 바꾸지 않는 것도 함께 본다.
 */
@ActiveProfiles("local")
class GallerySeedDataTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("모든 행이 단축어·이름·본문 규칙을 통과하고, 저장 규칙을 거쳐도 값이 바뀌지 않는다")
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
	@DisplayName("27개 모두 공개이고 업무 7·영업 6·고객응대 7·개인 7개, 노출 순서와 단축어가 겹치지 않는다")
	void seedComposition() {
		List<Map<String, Object>> rows = seedRows();

		assertThat(rows).hasSize(27).allMatch(GallerySeedDataTest::isPublished);
		assertThat(rows).extracting(row -> ((Number)row.get("id")).longValue())
			.containsExactlyElementsOf(LongStream.rangeClosed(1, 27).boxed().toList());
		Map<GalleryCategory, Integer> expected = Map.of(
			GalleryCategory.WORK, 7, GalleryCategory.SALES, 6,
			GalleryCategory.CUSTOMER_SERVICE, 7, GalleryCategory.PERSONAL, 7);
		expected.forEach((category, count) -> assertThat(rows)
			.filteredOn(row -> category.name().equals(row.get("category")))
			.as(category.name()).hasSize(count));
		assertThat(rows).extracting(row -> row.get("display_order")).doesNotHaveDuplicates();
		assertThat(rows).extracting(row -> Shortcut.create((String)row.get("shortcut")).getKey())
			.doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("전체 탭 맨 앞은 /견적이다")
	void quoteComesFirst() {
		String first = jdbcTemplate.queryForObject(
			"SELECT shortcut FROM gallery_templates WHERE is_published = 1 ORDER BY display_order, id LIMIT 1",
			String.class);

		assertThat(first).isEqualTo("/견적");
	}

	@Test
	@DisplayName("변수가 있는 템플릿과 없는 템플릿이 섞여 있고, /정산의 변수는 총금액·1인금액 2개다")
	void seedHasVariablesAndPlainContent() {
		List<String> contents = seedRows().stream().map(row -> (String)row.get("content")).toList();

		assertThat(contents).anyMatch(content -> content.contains("{{"));
		assertThat(contents).anyMatch(content -> !content.contains("{{"));
		assertThat(TemplateVariableExtractor.extract(contentOf("/정산"))).containsExactly("총금액", "1인금액");
	}

	/**
	 * 본문은 '\n' 이스케이프로 줄바꿈을 쓴다. DB가 역슬래시를 일반 글자로 보는 설정(NO_BACKSLASH_ESCAPES)이면
	 * 역슬래시+n 두 글자로 들어가는데, 이 경우도 규칙 검사는 통과하므로 따로 본다.
	 */
	@Test
	@DisplayName("본문의 줄바꿈은 진짜 LF이고, 역슬래시+n 두 글자나 CR이 없다")
	void seedContentHasRealLineFeeds() {
		List<String> contents = seedRows().stream().map(row -> (String)row.get("content")).toList();

		assertThat(contents).anyMatch(content -> content.contains("\n"));
		assertThat(contents).allSatisfy(content -> {
			assertThat(content).doesNotContain("\\n");
			assertThat(content).doesNotContain("\r");
		});
	}

	@Test
	@DisplayName("본문의 이모지가 깨지지 않고 저장된다 (4바이트 문자)")
	void seedKeepsEmoji() {
		assertThat(contentOf("/생일축하")).contains("🎂");
		assertThat(contentOf("/정산")).contains("🙏");
	}

	private List<Map<String, Object>> seedRows() {
		return jdbcTemplate.queryForList(
			"SELECT id, category, title, shortcut, content, display_order, is_published FROM gallery_templates ORDER BY id");
	}

	private String contentOf(String shortcut) {
		return jdbcTemplate.queryForObject("SELECT content FROM gallery_templates WHERE shortcut = ?", String.class,
			shortcut);
	}

	private static boolean isPublished(Map<String, Object> row) {
		Object value = row.get("is_published");
		return value instanceof Boolean bool ? bool : ((Number)value).intValue() == 1;
	}

}
