package com.sniptext.domain.gallery.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sniptext.domain.gallery.entity.GalleryTemplate;

public interface GalleryTemplateRepository extends JpaRepository<GalleryTemplate, Long> {

	/** 공개 템플릿 전체. 노출 순서, 같으면 ID 순 */
	List<GalleryTemplate> findAllByPublishedTrueOrderByDisplayOrderAscIdAsc();

	Optional<GalleryTemplate> findByIdAndPublishedTrue(Long id);

}
