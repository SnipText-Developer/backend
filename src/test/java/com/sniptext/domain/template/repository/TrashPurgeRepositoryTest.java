package com.sniptext.domain.template.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;

/** 휴지통 영구 삭제 쿼리의 조건(본인 소유 + 휴지통)과 지운 행 수를 실제 MySQL로 검증한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TrashPurgeRepositoryTest extends IntegrationTestSupport {

	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 20, 10, 0);

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
		clock.setTo(LocalDateTime.of(2026, 10, 25, 10, 0));
		userId = saveUser("owner@sniptext.com");
		otherUserId = saveUser("other@sniptext.com");
	}

	@Test
	@DisplayName("내 휴지통 템플릿 하나를 지우고 1을 돌려준다. 다른 행은 그대로다")
	void deleteOne() {
		Long target = saveTrashed(userId, "/견적");
		Long remaining = saveTrashed(userId, "/회의");

		assertThat(templateRepository.deleteTrashedByIdAndUserId(target, userId)).isEqualTo(1);

		assertThat(templateRepository.existsById(target)).isFalse();
		assertThat(templateRepository.existsById(remaining)).isTrue();
	}

	@Test
	@DisplayName("없는 ID, 다른 회원의 휴지통, 휴지통에 없는 내 템플릿은 지우지 않고 0을 돌려준다")
	void deleteOneNotMatched() {
		Long othersTrashed = saveTrashed(otherUserId, "/견적");
		Long myActive = saveActive(userId, "/회의");

		assertThat(templateRepository.deleteTrashedByIdAndUserId(Long.MAX_VALUE, userId)).isZero();
		assertThat(templateRepository.deleteTrashedByIdAndUserId(othersTrashed, userId)).isZero();
		assertThat(templateRepository.deleteTrashedByIdAndUserId(myActive, userId)).isZero();

		assertThat(templateRepository.existsById(othersTrashed)).isTrue();
		assertThat(templateRepository.existsById(myActive)).isTrue();
	}

	@Test
	@DisplayName("내 휴지통만 모두 지우고 지운 개수를 돌려준다. 활성 템플릿과 다른 회원의 휴지통은 그대로다")
	void deleteAll() {
		Long first = saveTrashed(userId, "/견적");
		Long second = saveTrashed(userId, "/견적");
		Long myActive = saveActive(userId, "/견적");
		Long othersTrashed = saveTrashed(otherUserId, "/회의");

		assertThat(templateRepository.deleteTrashedByUserId(userId)).isEqualTo(2);

		assertThat(templateRepository.existsById(first)).isFalse();
		assertThat(templateRepository.existsById(second)).isFalse();
		assertThat(templateRepository.existsById(myActive)).isTrue();
		assertThat(templateRepository.existsById(othersTrashed)).isTrue();
	}

	@Test
	@DisplayName("휴지통이 비어 있으면 0을 돌려준다")
	void deleteAllEmpty() {
		saveActive(userId, "/견적");

		assertThat(templateRepository.deleteTrashedByUserId(userId)).isZero();
	}

	/** 엔티티를 읽지 않고 지우므로 본문 복호화를 거치지 않는다 */
	@Test
	@DisplayName("본문을 복호화할 수 없는 행도 지운다")
	void undecryptableContent() {
		Long brokenOne = saveTrashed(userId, "/견적");
		Long brokenAll = saveTrashed(userId, "/회의");
		breakContent(brokenOne);
		breakContent(brokenAll);

		assertThat(templateRepository.deleteTrashedByIdAndUserId(brokenOne, userId)).isEqualTo(1);
		assertThat(templateRepository.deleteTrashedByUserId(userId)).isEqualTo(1);
	}

	/** 지운 뒤에 1차 캐시에 남은 엔티티를 돌려주면 이미 없는 행을 있다고 보게 된다 */
	@Test
	@DisplayName("지우기 전에 읽어 둔 엔티티가 있어도 지운 뒤 다시 찾으면 없다")
	void clearsPersistenceContext() {
		Long one = saveTrashed(userId, "/견적");
		Long all = saveTrashed(userId, "/회의");
		templateRepository.findById(one).orElseThrow();
		templateRepository.findById(all).orElseThrow();

		templateRepository.deleteTrashedByIdAndUserId(one, userId);
		templateRepository.deleteTrashedByUserId(userId);

		assertThat(templateRepository.findById(one)).isEmpty();
		assertThat(templateRepository.findById(all)).isEmpty();
	}

	/** 비우기 전에 1차 캐시를 비우면 아직 내보내지 않은 변경이 사라진다 */
	@Test
	@DisplayName("지우기 전에 쌓인 변경은 먼저 DB에 내보낸다")
	void flushesPendingChanges() {
		Template movedToTrash = templateRepository.saveAndFlush(TemplateFixture.template(userId, "/회의"));
		movedToTrash.moveToTrash(DELETED_AT);

		assertThat(templateRepository.deleteTrashedByUserId(userId)).isEqualTo(1);

		assertThat(templateRepository.existsById(movedToTrash.getId())).isFalse();
	}

	private void breakContent(Long templateId) {
		entityManager.createNativeQuery("update templates set content = 'not-encrypted' where id = :id")
			.setParameter("id", templateId)
			.executeUpdate();
		entityManager.clear();
	}

	private Long saveActive(Long ownerId, String shortcut) {
		return templateRepository.saveAndFlush(TemplateFixture.template(ownerId, shortcut)).getId();
	}

	private Long saveTrashed(Long ownerId, String shortcut) {
		return templateRepository.saveAndFlush(TemplateFixture.trashedTemplate(ownerId, shortcut, DELETED_AT))
			.getId();
	}

	private Long saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

}
