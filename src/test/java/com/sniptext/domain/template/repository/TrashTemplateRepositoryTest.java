package com.sniptext.domain.template.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.template.entity.Template;
import com.sniptext.domain.user.entity.User;
import com.sniptext.fixture.TemplateFixture;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;
import com.sniptext.support.MutableClock;
import com.sniptext.support.MutableClockConfiguration;

import jakarta.persistence.EntityManager;

/** 휴지통 조회의 정렬과 커서 조건을 실제 MySQL로 검증한다. 삭제 시각은 DATETIME(6) 그대로 비교되어야 한다. */
@Transactional
@Import(MutableClockConfiguration.class)
class TrashTemplateRepositoryTest extends IntegrationTestSupport {

	private static final LocalDateTime DELETED_AT = LocalDateTime.of(2026, 10, 7, 10, 58, 0, 123_456_000);
	private static final Limit LIMIT = Limit.of(10);

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
	@DisplayName("첫 페이지는 최근 삭제순이고, 같은 시각이면 ID가 큰 것이 먼저다")
	void firstPageOrder() {
		Long older = saveTrashed(userId, "/오래", DELETED_AT.minusSeconds(1));
		Long tiedFirst = saveTrashed(userId, "/동률1", DELETED_AT);
		Long tiedSecond = saveTrashed(userId, "/동률2", DELETED_AT);
		Long newer = saveTrashed(userId, "/최근", DELETED_AT.plusSeconds(1));

		assertThat(idsOf(templateRepository.findTrashFirstPage(userId, LIMIT)))
			.containsExactly(newer, tiedSecond, tiedFirst, older);
	}

	@Test
	@DisplayName("첫 페이지는 넘긴 개수만큼만 읽는다")
	void firstPageLimit() {
		saveTrashed(userId, "/가나", DELETED_AT);
		saveTrashed(userId, "/다라", DELETED_AT);

		assertThat(templateRepository.findTrashFirstPage(userId, Limit.of(1))).hasSize(1);
	}

	@Test
	@DisplayName("활성 템플릿과 다른 회원의 휴지통은 읽지 않는다")
	void onlyOwnTrash() {
		Long mine = saveTrashed(userId, "/내것", DELETED_AT);
		saveTrashed(otherUserId, "/남의것", DELETED_AT);
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/활성"));

		assertThat(idsOf(templateRepository.findTrashFirstPage(userId, LIMIT))).containsExactly(mine);
		assertThat(idsOf(templateRepository.findTrashAfter(userId, DELETED_AT.plusDays(1), 1L, LIMIT)))
			.containsExactly(mine);
	}

	@Test
	@DisplayName("다음 페이지는 커서보다 삭제가 오래된 항목과, 같은 시각의 더 작은 ID만 읽는다")
	void nextPageCondition() {
		Long older = saveTrashed(userId, "/오래", DELETED_AT.minusSeconds(1));
		Long tiedFirst = saveTrashed(userId, "/동률1", DELETED_AT);
		Long cursor = saveTrashed(userId, "/커서", DELETED_AT);
		saveTrashed(userId, "/동률3", DELETED_AT);
		saveTrashed(userId, "/최근", DELETED_AT.plusSeconds(1));

		assertThat(idsOf(templateRepository.findTrashAfter(userId, DELETED_AT, cursor, LIMIT)))
			.containsExactly(tiedFirst, older);
	}

	@Test
	@DisplayName("마이크로초만 다른 삭제 시각도 구분한다")
	void microsecondPrecision() {
		Long earlier = saveTrashed(userId, "/앞쪽", DELETED_AT);
		Long later = saveTrashed(userId, "/뒤쪽", DELETED_AT.plusNanos(1_000));

		assertThat(idsOf(templateRepository.findTrashAfter(userId, DELETED_AT.plusNanos(1_000), later, LIMIT)))
			.containsExactly(earlier);
	}

	@Test
	@DisplayName("휴지통 개수는 본인 휴지통만 센다")
	void count() {
		saveTrashed(userId, "/가나", DELETED_AT);
		saveTrashed(userId, "/다라", DELETED_AT);
		saveTrashed(otherUserId, "/마바", DELETED_AT);
		templateRepository.saveAndFlush(TemplateFixture.template(userId, "/활성"));

		assertThat(templateRepository.countByUserIdAndDeletedAtIsNotNull(userId)).isEqualTo(2L);
	}

	private Long saveTrashed(Long ownerId, String shortcut, LocalDateTime deletedAt) {
		return templateRepository.saveAndFlush(TemplateFixture.trashedTemplate(ownerId, shortcut, deletedAt)).getId();
	}

	private List<Long> idsOf(List<Template> templates) {
		return templates.stream().map(Template::getId).toList();
	}

	private Long saveUser(String email) {
		User user = UserFixture.user(email);
		entityManager.persist(user);
		entityManager.flush();
		return user.getId();
	}

}
