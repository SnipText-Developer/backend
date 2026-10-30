package com.sniptext.domain.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sniptext.domain.template.entity.Template;

public interface TemplateRepository extends JpaRepository<Template, Long> {

	/** 휴지통을 제외한 보유 개수. idx_templates_user_deleted (user_id, deleted_at)를 탄다 */
	long countByUserIdAndDeletedAtIsNull(Long userId);

	/**
	 * 활성 템플릿(휴지통 제외)에 같은 단축어 비교 키가 있는지. 생성 컬럼 active_shortcut_key는 휴지통이면 NULL이라
	 * 휴지통 조건을 따로 걸지 않아도 되고, 유니크 인덱스 uk_templates_user_active_shortcut을 그대로 탄다
	 */
	boolean existsByUserIdAndActiveShortcutKey(Long userId, String activeShortcutKey);

}
