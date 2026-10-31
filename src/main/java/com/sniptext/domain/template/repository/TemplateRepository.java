package com.sniptext.domain.template.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sniptext.domain.template.entity.Template;

public interface TemplateRepository extends JpaRepository<Template, Long> {

	/** 본인 소유 조회. 휴지통 템플릿도 찾으므로 내 휴지통과 다른 회원 것을 호출자가 구분할 수 있다 */
	Optional<Template> findByIdAndUserId(Long id, Long userId);

	/** 휴지통을 제외한 보유 개수. idx_templates_user_deleted (user_id, deleted_at)를 탄다 */
	long countByUserIdAndDeletedAtIsNull(Long userId);

	/**
	 * 활성 템플릿(휴지통 제외)에 같은 단축어 비교 키가 있는지. 생성 컬럼 active_shortcut_key는 휴지통이면 NULL이라
	 * 휴지통 조건을 따로 걸지 않아도 되고, 유니크 인덱스 uk_templates_user_active_shortcut을 그대로 탄다
	 */
	boolean existsByUserIdAndActiveShortcutKey(Long userId, String activeShortcutKey);

	/** existsByUserIdAndActiveShortcutKey에서 한 템플릿을 뺀 것. 자기 자신의 단축어는 중복이 아니므로 수정이 쓴다 */
	boolean existsByUserIdAndActiveShortcutKeyAndIdNot(Long userId, String activeShortcutKey, Long id);

	/**
	 * 넘긴 비교 키 중 활성 템플릿(휴지통 제외)이 이미 쓰는 키. 자동 번호 후보를 한 번에 확인할 때 쓴다.
	 * existsByUserIdAndActiveShortcutKey와 같이 생성 컬럼이라 휴지통 조건이 따로 없고, 유니크 인덱스를 그대로 탄다
	 */
	@Query("select t.activeShortcutKey from Template t where t.userId = :userId and t.activeShortcutKey in :keys")
	Set<String> findActiveShortcutKeys(@Param("userId") Long userId, @Param("keys") Collection<String> keys);

}
