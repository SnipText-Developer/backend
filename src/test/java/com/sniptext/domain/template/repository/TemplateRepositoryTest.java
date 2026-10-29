package com.sniptext.domain.template.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.time.LocalDateTime;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;

/** templates 테이블의 유니크·생성 컬럼·시각 정밀도를 실제 MySQL로 검증한다 (H2는 생성 컬럼 동작이 다르다). */
@Transactional
@Import(MutableClockConfiguration.class)
class TemplateRepositoryTest extends IntegrationTestSupport {

	private static final String ACTIVE_SHORTCUT_UNIQUE = "uk_templates_user_active_shortcut";
	/** DATETIME(6)과 같은 마이크로초 정밀도 */
	private static final LocalDateTime FIXED = LocalDateTime.of(2026, 10, 25, 10, 0, 0, 123_456_000);

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private MutableClock clock;

	private Long userId;
	private Long otherUserId;

	@BeforeEach
	void setUp() {
		clock.setTo(FIXED);
		userId = saveUser("owner@sniptext.com");
		otherUserId = saveUser("other@sniptext.com");
	}

	@Test
	@DisplayName("같은 회원이 같은 단축어 키로 두 번 저장하면 유니크 위반")
	void duplicateKeyForSameUser() {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));

		assertThatThrownBy(() -> templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(ACTIVE_SHORTCUT_UNIQUE);
	}

	/**
	 * 서비스는 저장 중 유니크 위반을 이 이름과 종류로 가려 TEMPLATE409_1로 바꾼다. 서비스 단위 테스트는 같은 모양의 가짜 예외를 쓰므로,
	 * MySQL·하이버네이트를 올리며 모양이 바뀌면 여기서 먼저 깨진다.
	 */
	@Test
	@DisplayName("단축어 유니크 위반은 하이버네이트가 테이블 접두어가 붙은 제약 이름과 UNIQUE 종류로 알려 준다")
	void duplicateViolationShape() {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));

		Throwable thrown = catchThrowable(() -> templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적")));

		assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
		ConstraintViolationException violation = findViolation(thrown);
		assertThat(violation.getKind()).isEqualTo(ConstraintKind.UNIQUE);
		assertThat(violation.getConstraintName()).isEqualTo("templates." + ACTIVE_SHORTCUT_UNIQUE);
	}

	@Test
	@DisplayName("/Quote와 /quote는 키가 같아 중복이다")
	void caseInsensitiveDuplicate() {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));

		assertThatThrownBy(() -> templateRepository.saveAndFlush(TemplateFixture.template(userId, "/quote")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining(ACTIVE_SHORTCUT_UNIQUE);
	}

	@Test
	@DisplayName("휴지통으로 옮긴 뒤에는 같은 단축어 키로 새로 저장할 수 있다")
	void trashReleasesShortcut() {
		Template trashed = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));
		trashed.moveToTrash(FIXED.plusHours(1));
		templateRepository.flush();

		Template saved = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));

		assertThat(saved.getId()).isNotNull();
	}

	@Test
	@DisplayName("다른 회원은 같은 단축어 키로 저장할 수 있다")
	void sameKeyForOtherUser() {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));

		Template saved = templateRepository.saveAndFlush(TemplateFixture.template(otherUserId, "/견적"));

		assertThat(saved.getId()).isNotNull();
	}

	@Test
	@DisplayName("생성 컬럼 active_shortcut_key는 활성이면 비교 키, 휴지통이면 NULL이다")
	void activeShortcutKeyGenerated() {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));
		assertThat(reload(template).getActiveShortcutKey()).isEqualTo("/quote");

		Template managed = templateRepository.findById(template.getId()).orElseThrow();
		managed.moveToTrash(FIXED.plusHours(1));
		templateRepository.flush();

		assertThat(reload(template).getActiveShortcutKey()).isNull();
	}

	@Test
	@DisplayName("저장하면 수정 시각이 생성 시각과 같은 Clock 시각으로 채워진다")
	void editedAtFilledOnSave() {
		Template template = TemplateFixture.template(userId);
		assertThat(template.getEditedAt()).isNull();

		templateRepository.saveAndFlush(template);

		assertThat(template.getEditedAt()).isEqualTo(FIXED).isEqualTo(template.getCreatedAt());
		assertThat(reload(template).getEditedAt()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("같은 값으로 수정하면 UPDATE가 나가지 않아 수정 시각과 updated_at이 그대로다")
	void sameValuesEditDoesNotUpdate() {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId));
		clock.advance(Duration.ofHours(1));

		template.edit(template.getTitle(), template.getShortcut(), template.getContent(), LocalDateTime.now(clock));
		templateRepository.flush();

		Template found = reload(template);
		assertThat(found.getEditedAt()).isEqualTo(FIXED);
		assertThat(found.getUpdatedAt()).isEqualTo(FIXED);
	}

	@Test
	@DisplayName("저장 후 다시 읽은 시각이 저장 전 엔티티 값과 같다")
	void timesSurviveRoundTrip() {
		Template template = TemplateFixture.template(userId);
		template.markUsed(FIXED.plusNanos(1_000));
		template.moveToTrash(FIXED.plusNanos(2_000));
		templateRepository.saveAndFlush(template);

		Template found = reload(template);

		assertThat(found.getCreatedAt()).isEqualTo(FIXED);
		assertThat(found.getUpdatedAt()).isEqualTo(FIXED);
		assertThat(found.getEditedAt()).isEqualTo(FIXED);
		assertThat(found.getLastUsedAt()).isEqualTo(FIXED.plusNanos(1_000));
		assertThat(found.getDeletedAt()).isEqualTo(FIXED.plusNanos(2_000));
	}

	@Test
	@DisplayName("이름·본문·단축어가 각 컬럼에 저장되고 그대로 읽힌다 (본문의 줄바꿈과 변수 표기 포함)")
	void valuesRoundTrip() {
		Template template = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));

		Template found = reload(template);

		assertThat(found.getTitle().getValue()).isEqualTo(TemplateFixture.TITLE);
		assertThat(found.getContent().getValue()).isEqualTo(TemplateFixture.CONTENT);
		assertThat(found.getShortcut().getValue()).isEqualTo("/Quote");
		assertThat(found.getShortcut().getKey()).isEqualTo("/quote");
		assertThat(found.getUserId()).isEqualTo(userId);
		assertThat(found.getSourceGalleryTemplateId()).isNull();
	}

	@Test
	@DisplayName("활성 개수는 휴지통과 다른 회원의 템플릿을 세지 않는다")
	void countActive() {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/견적"));
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/감사"));
		Template trashed = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/공지"));
		trashed.moveToTrash(FIXED.plusHours(1));
		templateRepository.saveAndFlush(TemplateFixture.template(otherUserId, "/견적"));

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(userId)).isEqualTo(2);
		assertThat(templateRepository.countByUserIdAndDeletedAtIsNull(otherUserId)).isEqualTo(1);
	}

	@Test
	@DisplayName("활성 단축어 키 존재 여부는 비교 키로 찾고, 휴지통과 다른 회원의 템플릿은 보지 않는다")
	void existsActiveShortcutKey() {
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/Quote"));
		Template trashed = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/공지"));
		trashed.moveToTrash(FIXED.plusHours(1));
		templateRepository.saveAndFlush(TemplateFixture.template(otherUserId, "/감사"));
		entityManager.clear();

		assertThat(templateRepository.existsByUserIdAndActiveShortcutKey(userId, "/quote")).isTrue();
		assertThat(templateRepository.existsByUserIdAndActiveShortcutKey(userId, "/공지")).isFalse();
		assertThat(templateRepository.existsByUserIdAndActiveShortcutKey(userId, "/감사")).isFalse();
	}

	private Long saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

	private static ConstraintViolationException findViolation(Throwable thrown) {
		for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return violation;
			}
		}
		throw new AssertionError("원인 사슬에 하이버네이트 ConstraintViolationException이 없다");
	}

	private Template reload(Template template) {
		entityManager.clear();
		return entityManager.find(Template.class, template.getId());
	}

}
