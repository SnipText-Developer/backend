package com.sniptext.domain.gallery.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.gallery.entity.GalleryCategory;
import com.sniptext.domain.gallery.entity.GalleryTemplate;
import com.sniptext.fixture.GalleryTemplateFixture;
import com.sniptext.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

/** gallery_templates의 공개 여부·정렬 조건을 실제 MySQL로 검증한다. */
@Transactional
class GalleryTemplateRepositoryTest extends IntegrationTestSupport {

	@Autowired
	private GalleryTemplateRepository galleryTemplateRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("목록은 비공개를 빼고 노출 순서, 같으면 ID 순으로 돌려준다")
	void findAllPublishedOrdered() {
		GalleryTemplate second = save(GalleryTemplateFixture.published(20));
		GalleryTemplate hidden = save(GalleryTemplateFixture.unpublished(5));
		GalleryTemplate thirdSameOrder = save(GalleryTemplateFixture.published(20));
		GalleryTemplate first = save(GalleryTemplateFixture.published(10));
		entityManager.clear();

		List<GalleryTemplate> found = galleryTemplateRepository.findAllByPublishedTrueOrderByDisplayOrderAscIdAsc();

		assertThat(found).extracting(GalleryTemplate::getId)
			.containsExactly(first.getId(), second.getId(), thirdSameOrder.getId())
			.doesNotContain(hidden.getId());
	}

	@Test
	@DisplayName("상세는 공개면 찾고, 비공개·없는 ID면 빈 값이다")
	void findPublishedById() {
		GalleryTemplate published = save(GalleryTemplateFixture.published(10));
		GalleryTemplate hidden = save(GalleryTemplateFixture.unpublished(10));
		entityManager.clear();

		assertThat(galleryTemplateRepository.findByIdAndPublishedTrue(published.getId())).isPresent();
		assertThat(galleryTemplateRepository.findByIdAndPublishedTrue(hidden.getId())).isEmpty();
		assertThat(galleryTemplateRepository.findByIdAndPublishedTrue(-1L)).isEmpty();
	}

	@Test
	@DisplayName("모든 컬럼이 그대로 저장되고 읽힌다 (is_published는 boolean, slug는 비어 있음)")
	void valuesRoundTrip() {
		GalleryTemplate saved = save(GalleryTemplateFixture.galleryTemplate(GalleryCategory.CUSTOMER_SERVICE,
			"배송 지연 안내", "/배송지연", "본문\n둘째 줄", 7, false));
		entityManager.clear();

		GalleryTemplate found = galleryTemplateRepository.findById(saved.getId()).orElseThrow();

		assertThat(found.getCategory()).isEqualTo(GalleryCategory.CUSTOMER_SERVICE);
		assertThat(found.getTitle()).isEqualTo("배송 지연 안내");
		assertThat(found.getShortcut()).isEqualTo("/배송지연");
		assertThat(found.getContent()).isEqualTo("본문\n둘째 줄");
		assertThat(found.getDisplayOrder()).isEqualTo(7);
		assertThat(found.isPublished()).isFalse();
		assertThat(found.getSlug()).isNull();
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	private GalleryTemplate save(GalleryTemplate galleryTemplate) {
		return galleryTemplateRepository.saveAndFlush(galleryTemplate);
	}

}
