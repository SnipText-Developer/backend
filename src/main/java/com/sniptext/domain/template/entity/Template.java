package com.sniptext.domain.template.entity;

import java.time.LocalDateTime;

import com.sniptext.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내 템플릿 (휴지통 포함). 시각 컬럼은 서로 섞지 않는다.
 * edited_at은 이름·단축어·본문 수정, last_used_at은 사용 기록, deleted_at은 휴지통 이동에서만 바뀐다.
 * updated_at은 모든 변경에서 감사 기능이 기록한다 (확장 동기화 기준).
 * 이름·단축어·본문의 규칙은 각 값 객체가 갖고, 여기서는 필수 값만 검사한다.
 */
@Getter
@Entity
@Table(name = "templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Template extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 갤러리에서 저장한 경우 원본 ID. 원본이 지워지면 DB가 NULL로 바꾼다 */
	@Column(name = "source_gallery_template_id")
	private Long sourceGalleryTemplateId;

	@Embedded
	private TemplateTitle title;

	@Embedded
	private Shortcut shortcut;

	/** DB 생성 컬럼. 휴지통이면 NULL이 되어 단축어 점유가 풀린다. 값을 직접 넣지 않는다 */
	@Column(name = "active_shortcut_key", length = 11, insertable = false, updatable = false)
	private String activeShortcutKey;

	@Embedded
	private TemplateContent content;

	@Column(name = "last_used_at")
	private LocalDateTime lastUsedAt;

	@Column(name = "edited_at", nullable = false)
	private LocalDateTime editedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Builder(access = AccessLevel.PRIVATE)
	private Template(Long userId, Long sourceGalleryTemplateId, TemplateTitle title, Shortcut shortcut,
		TemplateContent content) {
		this.userId = userId;
		this.sourceGalleryTemplateId = sourceGalleryTemplateId;
		this.title = title;
		this.shortcut = shortcut;
		this.content = content;
	}

	public static Template create(Long userId, TemplateTitle title, Shortcut shortcut, TemplateContent content) {
		require(userId != null, "회원 ID가 없다");
		validateValues(title, shortcut, content);
		return Template.builder()
			.userId(userId)
			.title(title)
			.shortcut(shortcut)
			.content(content)
			.build();
	}

	/** 갤러리 템플릿을 내 템플릿으로 저장한 것. 같은 원본을 여러 번 저장할 수 있다 */
	public static Template createFromGallery(Long userId, Long sourceGalleryTemplateId, TemplateTitle title,
		Shortcut shortcut, TemplateContent content) {
		require(userId != null, "회원 ID가 없다");
		require(sourceGalleryTemplateId != null, "갤러리 원본 ID가 없다");
		validateValues(title, shortcut, content);
		return Template.builder()
			.userId(userId)
			.sourceGalleryTemplateId(sourceGalleryTemplateId)
			.title(title)
			.shortcut(shortcut)
			.content(content)
			.build();
	}

	/** 세 값이 모두 기존과 같으면 아무것도 바꾸지 않는다 (전체 수정이라 편집 화면을 그대로 저장해도 호출된다) */
	public void edit(TemplateTitle title, Shortcut shortcut, TemplateContent content, LocalDateTime now) {
		validateValues(title, shortcut, content);
		require(now != null, "수정 시각이 없다");
		if (title.equals(this.title) && shortcut.equals(this.shortcut) && content.equals(this.content)) {
			return;
		}
		this.title = title;
		this.shortcut = shortcut;
		this.content = content;
		this.editedAt = now;
	}

	public void markUsed(LocalDateTime now) {
		require(now != null, "사용 시각이 없다");
		this.lastUsedAt = now;
	}

	public void moveToTrash(LocalDateTime now) {
		require(now != null, "휴지통 이동 시각이 없다");
		this.deletedAt = now;
	}

	public boolean isInTrash() {
		return deletedAt != null;
	}

	/**
	 * 처음 저장할 때 수정 시각을 생성 시각과 맞춘다. JPA는 @EntityListeners(감사 기능)를 엔티티 자신의 콜백보다
	 * 먼저 부르므로 이 시점에는 created_at이 Clock 시각으로 채워져 있다.
	 */
	@PrePersist
	private void initEditedAt() {
		if (editedAt == null) {
			editedAt = getCreatedAt();
		}
	}

	private static void validateValues(TemplateTitle title, Shortcut shortcut, TemplateContent content) {
		require(title != null, "이름이 없다");
		require(shortcut != null, "단축어가 없다");
		require(content != null, "본문이 없다");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
