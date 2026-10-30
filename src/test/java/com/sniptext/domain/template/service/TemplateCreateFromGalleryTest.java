package com.sniptext.domain.template.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willReturn;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.sniptext.domain.gallery.repository.GalleryTemplateRepository;
import com.sniptext.domain.plan.service.PlanQueryService;
import com.sniptext.domain.plan.service.dto.result.PlanResult;
import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.template.exception.TemplateErrorCode;
import com.sniptext.domain.template.repository.TemplateRepository;
import com.sniptext.domain.template.service.dto.command.TemplateCreateFromGalleryCommand;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAssignResult;
import com.sniptext.domain.user.entity.Plan;
import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.GalleryTemplateFixture;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.global.exception.BusinessException;
import com.sniptext.support.IntegrationTestSupport;

/**
 * 갤러리 저장의 한도·자동 번호를 실제 MySQL로 검증한다 (생성 컬럼·외래키·잠금이 다 걸린다).
 * 클래스에 @Transactional을 붙이지 않는다. 실제 저장 흐름의 유니크 위반까지 보기 위해서다. 대신 만든 행을 직접 지운다.
 */
class TemplateCreateFromGalleryTest extends IntegrationTestSupport {

	private static final String TITLE = "프리랜서 견적 안내";
	private static final String CONTENT = "안녕하세요 {{고객명}}님";
	private static final LocalDateTime TRASHED_AT = LocalDateTime.of(2026, 10, 25, 10, 0);
	/** 2~99 전부를 채우려면 플랜 한도(PRO 50)보다 많은 행이 필요해 한도만 넓혀 둔다 */
	private static final PlanResult WIDE_LIMIT = new PlanResult(Plan.PRO, 200, 10);

	@Autowired
	private TemplateCommandService templateCommandService;

	@MockitoSpyBean
	private TemplateRepository templateRepository;

	@MockitoSpyBean
	private PlanQueryService planQueryService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private GalleryTemplateRepository galleryTemplateRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private final List<Long> userIds = new ArrayList<>();
	private Long userId;
	private Long galleryTemplateId;

	@BeforeEach
	void setUp() {
		userId = saveFreeUser("gallery-save@sniptext.com");
		galleryTemplateId = galleryTemplateRepository.save(GalleryTemplateFixture.published(1)).getId();
	}

	// 회원을 지우면 외래키(ON DELETE CASCADE)로 템플릿도 지워진다
	@AfterEach
	void tearDown() {
		userIds.forEach(userRepository::deleteById);
		galleryTemplateRepository.deleteById(galleryTemplateId);
	}

	@Test
	@DisplayName("충돌이 없으면 원본 단축어로 저장하고 출처를 기록하며 본문을 그대로 복사한다")
	void savedWithSource() {
		TemplateShortcutAssignResult result = save("/견적");

		assertThat(result.shortcut()).isEqualTo("/견적");
		assertThat(result.originalShortcut()).isEqualTo("/견적");
		assertThat(result.shortcutChanged()).isFalse();
		assertThat(result.title()).isEqualTo(TITLE);
		Template saved = templateRepository.findById(result.templateId()).orElseThrow();
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getSourceGalleryTemplateId()).isEqualTo(galleryTemplateId);
		assertThat(saved.getContent().getValue()).isEqualTo(CONTENT);
		assertThat(saved.getActiveShortcutKey()).isEqualTo("/견적");
	}

	@Test
	@DisplayName("같은 갤러리 템플릿을 여러 번 저장하면 /견적 → /견적2 → /견적3으로 하나씩 늘어난다")
	void sameGalleryTemplateRepeatedly() {
		assertThat(save("/견적").shortcut()).isEqualTo("/견적");
		assertThat(save("/견적").shortcut()).isEqualTo("/견적2");

		TemplateShortcutAssignResult third = save("/견적");

		assertThat(third.shortcut()).isEqualTo("/견적3");
		assertThat(third.originalShortcut()).isEqualTo("/견적");
		assertThat(third.shortcutChanged()).isTrue();
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(3);
	}

	@Test
	@DisplayName("FREE 4개에서 5개째는 저장된다")
	void belowLimit() {
		saveTemplates(userId, "/t1", "/t2", "/t3", "/t4");

		TemplateShortcutAssignResult result = save("/견적");

		assertThat(result.templateId()).isNotNull();
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(5);
	}

	@Test
	@DisplayName("FREE 5개에서 6개째는 TEMPLATE403_1이고 행이 늘지 않는다")
	void limitReached() {
		saveTemplates(userId, "/t1", "/t2", "/t3", "/t4", "/t5");

		assertErrorCode(() -> save("/견적"), TemplateErrorCode.TEMPLATE_LIMIT_REACHED);

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(5);
	}

	@Test
	@DisplayName("휴지통 템플릿은 한도에 세지 않는다")
	void trashNotCountedInLimit() {
		saveTemplates(userId, "/t1", "/t2", "/t3", "/t4");
		saveTrashed(userId, "/t5");

		assertThat(save("/견적").templateId()).isNotNull();
	}

	@Test
	@DisplayName("한도와 자동 번호 소진이 겹치면 한도가 먼저다")
	void limitBeforeExhausted() {
		willReturn(new PlanResult(Plan.PRO, 99, 10)).given(planQueryService).getCurrentPlan(userId);
		fillOriginalAndNumbersUpTo("/견적", 99);

		assertErrorCode(() -> save("/견적"), TemplateErrorCode.TEMPLATE_LIMIT_REACHED);
	}

	@Test
	@DisplayName("휴지통에 있는 같은 단축어는 충돌이 아니라 원본 그대로 저장한다")
	void trashedShortcutIsNotConflict() {
		saveTrashed(userId, "/견적");

		TemplateShortcutAssignResult result = save("/견적");

		assertThat(result.shortcut()).isEqualTo("/견적");
		assertThat(result.shortcutChanged()).isFalse();
	}

	@Test
	@DisplayName("휴지통이 차지했던 번호는 비어 있으므로 그 번호를 쓴다")
	void trashedNumberIsAvailable() {
		saveTemplates(userId, "/견적");
		saveTrashed(userId, "/견적2");

		assertThat(save("/견적").shortcut()).isEqualTo("/견적2");
	}

	@Test
	@DisplayName("대소문자만 다른 단축어는 충돌이라 번호를 붙이고, 원본의 대소문자는 그대로 둔다")
	void caseInsensitiveConflict() {
		saveTemplates(userId, "/ABC");

		TemplateShortcutAssignResult result = save("/Abc");

		assertThat(result.shortcut()).isEqualTo("/Abc2");
		assertThat(result.originalShortcut()).isEqualTo("/Abc");
		assertThat(result.shortcutChanged()).isTrue();
	}

	@Test
	@DisplayName("번호 후보도 대소문자를 무시해 비교한다")
	void caseInsensitiveNumberedConflict() {
		saveTemplates(userId, "/abc", "/ABC2");

		assertThat(save("/Abc").shortcut()).isEqualTo("/Abc3");
	}

	@Test
	@DisplayName("다른 회원의 같은 단축어는 영향이 없다")
	void otherUserShortcutIgnored() {
		Long otherUserId = saveFreeUser("gallery-other@sniptext.com");
		saveTemplates(otherUserId, "/견적");

		TemplateShortcutAssignResult result = save("/견적");

		assertThat(result.shortcut()).isEqualTo("/견적");
		assertThat(result.shortcutChanged()).isFalse();
	}

	@ParameterizedTest
	@CsvSource({
		"/견적2,          /견적22",
		"/견적요청서작성안내문, /견적요청서작성안내2",
		"/견적요청서작성안내2, /견적요청서작성안내3"
	})
	@DisplayName("숫자로 끝나는 원본도 뒤에 붙이고, / 뒤가 10자를 넘으면 원본을 뒤에서 잘라 맞춘다")
	void numberedShortcut(String original, String expected) {
		saveTemplates(userId, original);

		TemplateShortcutAssignResult result = save(original);

		assertThat(result.shortcut()).isEqualTo(expected);
		assertThat(result.originalShortcut()).isEqualTo(original);
		assertThat(result.shortcutChanged()).isTrue();
	}

	@Test
	@DisplayName("번호가 두 자리가 되면 원본을 한 글자 더 잘라 / 뒤 10자를 맞춘다")
	void twoDigitNumberTruncates() {
		Long proUserId = savePlanUser("gallery-pro@sniptext.com", Plan.PRO);
		List<String> shortcuts = new ArrayList<>(List.of("/견적요청서작성안내문"));
		IntStream.rangeClosed(2, 9).forEach(number -> shortcuts.add("/견적요청서작성안내" + number));
		saveTemplates(proUserId, shortcuts.toArray(String[]::new));

		TemplateShortcutAssignResult result = templateCommandService.createFromGallery(
			new TemplateCreateFromGalleryCommand(proUserId, galleryTemplateId, TITLE, "/견적요청서작성안내문", CONTENT));

		assertThat(result.shortcut()).isEqualTo("/견적요청서작성안10");
		assertThat(result.shortcutChanged()).isTrue();
	}

	@Test
	@DisplayName("98번까지 차 있으면 마지막 번호 99를 쓴다")
	void lastNumber() {
		willReturn(WIDE_LIMIT).given(planQueryService).getCurrentPlan(userId);
		fillOriginalAndNumbersUpTo("/견적", 98);

		assertThat(save("/견적").shortcut()).isEqualTo("/견적99");
	}

	@Test
	@DisplayName("2~99가 모두 차면 TEMPLATE409_2이고 행이 늘지 않는다")
	void numbersExhausted() {
		willReturn(WIDE_LIMIT).given(planQueryService).getCurrentPlan(userId);
		fillOriginalAndNumbersUpTo("/견적", 99);

		assertErrorCode(() -> save("/견적"), TemplateErrorCode.SHORTCUT_NUMBER_EXHAUSTED);

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(99);
	}

	@Test
	@DisplayName("사용 중인 키 조회를 통과해도 저장에서 유니크 위반이 나면 TEMPLATE409_1이고 행이 늘지 않는다")
	void uniqueViolationBecomesConflict() {
		saveTemplates(userId, "/견적");
		willReturn(Set.of()).given(templateRepository).findActiveShortcutKeys(anyLong(), anyCollection());

		assertErrorCode(() -> save("/견적"), TemplateErrorCode.SHORTCUT_DUPLICATED);

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(1);
	}

	/**
	 * 호출자가 먼저 일반 조회를 하면 그 스냅샷 때문에 잠금 뒤 개수 검사가 옛 값을 봐서 한도를 넘는다.
	 * 조회가 없는 트랜잭션도 언제든 조회가 앞에 끼어들 수 있으므로 바깥 트랜잭션 자체를 막는다.
	 */
	@Test
	@DisplayName("호출자 트랜잭션 안에서 부르면 IllegalStateException이고 저장하지 않는다")
	void insideOuterTransaction() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			galleryTemplateRepository.findById(galleryTemplateId).orElseThrow();
			save("/견적");
		}))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("갤러리 저장은 호출자 트랜잭션 밖에서 불러야 한다");

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isZero();
	}

	private TemplateShortcutAssignResult save(String shortcut) {
		return templateCommandService.createFromGallery(
			new TemplateCreateFromGalleryCommand(userId, galleryTemplateId, TITLE, shortcut, CONTENT));
	}

	private Long saveFreeUser(String email) {
		return savePlanUser(email, null);
	}

	private Long savePlanUser(String email, Plan plan) {
		Long id = userRepository.save(UserFixture.userWithPlanOverride(email, plan)).getId();
		userIds.add(id);
		return id;
	}

	private void saveTemplates(Long ownerId, String... shortcuts) {
		for (String shortcut : shortcuts) {
			templateRepository.save(TemplateFixture.template(ownerId, shortcut));
		}
	}

	private void saveTrashed(Long ownerId, String shortcut) {
		Template template = TemplateFixture.template(ownerId, shortcut);
		template.moveToTrash(TRASHED_AT);
		templateRepository.save(template);
	}

	/** 원본과 번호 2~lastNumber를 활성 템플릿으로 채운다 */
	private void fillOriginalAndNumbersUpTo(String shortcut, int lastNumber) {
		saveTemplates(userId, shortcut);
		IntStream.rangeClosed(2, lastNumber).forEach(number -> saveTemplates(userId, shortcut + number));
	}

	private static void assertErrorCode(Runnable action, TemplateErrorCode errorCode) {
		assertThatThrownBy(action::run)
			.isInstanceOf(BusinessException.class)
			.extracting("resultCode")
			.isEqualTo(errorCode);
	}

}
