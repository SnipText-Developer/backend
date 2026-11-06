package com.sniptext.domain.gallery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import com.sniptext.domain.gallery.repository.GalleryTemplateRepository;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.GalleryTemplateFixture;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;

/**
 * 명세 21번. 상태 코드·응답 필드·에러 변환·인증·캐시 헤더를 실제 필터까지 거쳐 확인한다.
 * 한도·자동 번호 규칙의 세부 경우는 TemplateCreateFromGalleryTest가 맡는다.
 * 클래스에 @Transactional을 붙이지 않는다. 붙이면 createFromGallery가 바깥 트랜잭션으로 보고 예외를 던진다.
 * 그래서 저장이 성공하는 것으로 갤러리 저장 서비스가 트랜잭션 없이 부른다는 것도 확인된다. 대신 만든 행을 직접 지운다.
 */
class GallerySaveControllerTest extends IntegrationTestSupport {

	private static final String SAVE_PATH = "/api/v1/gallery/templates/{id}/save";
	private static final String NO_STORE = "no-store";

	@MockitoSpyBean
	private TemplateRepository templateRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private GalleryTemplateRepository galleryTemplateRepository;

	private final List<Long> galleryTemplateIds = new ArrayList<>();
	private Long userId;
	private Long galleryTemplateId;

	@BeforeEach
	void setUp() {
		userId = userRepository.save(UserFixture.user("gallery-save-api@sniptext.com")).getId();
		galleryTemplateId = saveGalleryTemplate(true);
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다. 갤러리는 이 테스트가 만든 행만 지운다
	@AfterEach
	void tearDown() {
		userRepository.deleteById(userId);
		galleryTemplateIds.forEach(galleryTemplateRepository::deleteById);
	}

	@Test
	@DisplayName("단축어가 겹치지 않으면 201, 원본 단축어 그대로 저장하고 응답 필드 5개를 담는다")
	void saved() throws Exception {
		save(galleryTemplateId)
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("GALLERY201_1"))
			.andExpect(jsonPath("$.message").value("내 템플릿에 저장되었습니다."))
			.andExpect(jsonPath("$.result.templateId").isNumber())
			.andExpect(jsonPath("$.result.title").value(GalleryTemplateFixture.TITLE))
			.andExpect(jsonPath("$.result.shortcut").value(GalleryTemplateFixture.SHORTCUT))
			.andExpect(jsonPath("$.result.originalShortcut").value(GalleryTemplateFixture.SHORTCUT))
			.andExpect(jsonPath("$.result.shortcutChanged").value(false))
			.andExpect(jsonPath("$.result.length()").value(5));
	}

	@Test
	@DisplayName("단축어가 겹치면 /견적2로 저장하고 originalShortcut은 원본, shortcutChanged는 true다")
	void shortcutNumbered() throws Exception {
		templateRepository.save(TemplateFixture.template(userId, GalleryTemplateFixture.SHORTCUT));

		save(galleryTemplateId)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.result.shortcut").value("/견적2"))
			.andExpect(jsonPath("$.result.originalShortcut").value(GalleryTemplateFixture.SHORTCUT))
			.andExpect(jsonPath("$.result.shortcutChanged").value(true));
	}

	@Test
	@DisplayName("같은 갤러리 템플릿을 두 번 저장하면 템플릿이 2개 생기고 둘 다 출처가 기록된다")
	void savedTwice() throws Exception {
		save(galleryTemplateId).andExpect(status().isCreated());
		save(galleryTemplateId).andExpect(status().isCreated());

		assertThat(myTemplates())
			.hasSize(2)
			.extracting(Template::getSourceGalleryTemplateId)
			.containsOnly(galleryTemplateId);
	}

	@Test
	@DisplayName("활성 템플릿 50개에서 저장하면 TEMPLATE403_1이고 개수가 그대로다")
	void limitReached() throws Exception {
		templateRepository.saveAll(IntStream.rangeClosed(1, 50)
			.mapToObj(number -> TemplateFixture.template(userId, "/t" + number))
			.toList());

		expectError(save(galleryTemplateId), 403, "TEMPLATE403_1", "템플릿 저장 한도에 도달했어요.");

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(50);
	}

	@Test
	@DisplayName("없는 갤러리 템플릿 ID면 GALLERY404_1이고 내 템플릿이 생기지 않는다")
	void missingGalleryTemplate() throws Exception {
		expectError(save(Long.MAX_VALUE), 404, "GALLERY404_1", "갤러리 템플릿을 찾을 수 없어요.");

		assertThat(myTemplates()).isEmpty();
	}

	@Test
	@DisplayName("비공개 갤러리 템플릿이면 GALLERY404_1이고 내 템플릿이 생기지 않는다")
	void unpublishedGalleryTemplate() throws Exception {
		Long hiddenId = saveGalleryTemplate(false);

		expectError(save(hiddenId), 404, "GALLERY404_1", "갤러리 템플릿을 찾을 수 없어요.");

		assertThat(myTemplates()).isEmpty();
	}

	@Test
	@DisplayName("토큰 없이 요청하면 COMMON401_1, 캐시 금지")
	void unauthenticated() throws Exception {
		expectError(mockMvc.perform(post(SAVE_PATH, galleryTemplateId)), 401, "COMMON401_1", "로그인이 필요해요.");

		assertThat(myTemplates()).isEmpty();
	}

	/** 번호 2~99 소진은 한도 50에서 실제로 닿지 않는다. 후보 키를 모두 사용 중으로 돌려줘 서비스의 예외가 응답으로 바뀌는지만 본다 */
	@Test
	@DisplayName("자동 번호를 모두 쓰면 TEMPLATE409_2 에러 응답이다")
	void shortcutNumberExhausted() throws Exception {
		willAnswer(invocation -> Set.copyOf(invocation.<Collection<String>>getArgument(1)))
			.given(templateRepository).findActiveShortcutKeys(anyLong(), anyCollection());

		expectError(save(galleryTemplateId), 409, "TEMPLATE409_2", "사용할 수 있는 단축어 번호가 없어요.");

		assertThat(myTemplates()).isEmpty();
	}

	private ResultActions save(Long id) throws Exception {
		return mockMvc.perform(post(SAVE_PATH, id).header(HttpHeaders.AUTHORIZATION, bearer(userId)));
	}

	private static void expectError(ResultActions actions, int status, String code, String message) throws Exception {
		actions
			.andExpect(status().is(status))
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, NO_STORE))
			.andExpect(jsonPath("$.isSuccess").value(false))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.message").value(message))
			.andExpect(jsonPath("$.result").isEmpty());
	}

	private Long saveGalleryTemplate(boolean published) {
		Long id = galleryTemplateRepository.save(
			published ? GalleryTemplateFixture.published(1) : GalleryTemplateFixture.unpublished(1)).getId();
		galleryTemplateIds.add(id);
		return id;
	}

	private List<Template> myTemplates() {
		return templateRepository.findAll().stream()
			.filter(template -> template.getUserId().equals(userId))
			.toList();
	}

}
