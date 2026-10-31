package com.sniptext.domain.template.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sniptext.domain.template.entity.Template;

public interface TemplateRepository extends JpaRepository<Template, Long> {

	/** 휴지통을 제외한 보유 개수. idx_templates_user_deleted (user_id, deleted_at)를 탄다 */
	long countByUserIdAndDeletedAtIsNull(Long userId);

	/**
	 * 활성 템플릿(휴지통 제외)에 같은 단축어 비교 키가 있는지. 생성 컬럼 active_shortcut_key는 휴지통이면 NULL이라
	 * 휴지통 조건을 따로 걸지 않아도 되고, 유니크 인덱스 uk_templates_user_active_shortcut을 그대로 탄다
	 */
	boolean existsByUserIdAndActiveShortcutKey(Long userId, String activeShortcutKey);

	/**
	 * 넘긴 비교 키 중 활성 템플릿(휴지통 제외)이 이미 쓰는 키. 자동 번호 후보를 한 번에 확인할 때 쓴다.
	 * existsByUserIdAndActiveShortcutKey와 같이 생성 컬럼이라 휴지통 조건이 따로 없고, 유니크 인덱스를 그대로 탄다
	 */
	@Query("select t.activeShortcutKey from Template t where t.userId = :userId and t.activeShortcutKey in :keys")
	Set<String> findActiveShortcutKeys(@Param("userId") Long userId, @Param("keys") Collection<String> keys);

	/** 휴지통 개수. idx_templates_user_deleted (user_id, deleted_at)를 탄다 */
	long countByUserIdAndDeletedAtIsNotNull(Long userId);

	/**
	 * 휴지통 첫 페이지. 최근 삭제순, 같은 시각이면 ID가 큰 것부터.
	 * idx_templates_user_deleted는 뒤에 기본 키가 붙어 있어 이 정렬을 인덱스 역방향 스캔으로 처리한다
	 */
	@Query("""
		select t from Template t
		where t.userId = :userId and t.deletedAt is not null
		order by t.deletedAt desc, t.id desc
		""")
	List<Template> findTrashFirstPage(@Param("userId") Long userId, Limit limit);

	/** 휴지통 다음 페이지. 정렬이 같고, 커서가 가리키는 항목 바로 다음부터 읽는다. deleted_at 비교가 있어 휴지통만 걸린다 */
	@Query("""
		select t from Template t
		where t.userId = :userId
		and (t.deletedAt < :deletedAt or (t.deletedAt = :deletedAt and t.id < :id))
		order by t.deletedAt desc, t.id desc
		""")
	List<Template> findTrashAfter(@Param("userId") Long userId, @Param("deletedAt") LocalDateTime deletedAt,
		@Param("id") Long id, Limit limit);

}
