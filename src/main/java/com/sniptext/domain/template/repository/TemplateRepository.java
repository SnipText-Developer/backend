package com.sniptext.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sniptext.domain.template.entity.Template;

public interface TemplateRepository extends JpaRepository<Template, Long> {

	/** 휴지통을 제외한 보유 개수. idx_templates_user_deleted (user_id, deleted_at)를 탄다 */
	long countByUserIdAndDeletedAtIsNull(Long userId);

}
