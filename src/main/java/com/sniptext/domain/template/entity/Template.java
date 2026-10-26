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
 * <p>
 * 이름·단축어·본문의 규칙은 각 값 객체가 갖는다. 팩토리와 변경 메서드는 필수 값만 검사하고,
 * 위반은 검사를 빠뜨린 버그라 IllegalArgumentException을 던진다.
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

    /**
     * 갤러리에서 저장한 경우 원본 ID. 원본이 지워지면 DB가 NULL로 바꾼다
     */
    @Column(name = "source_gallery_template_id")
    private Long sourceGalleryTemplateId;

    @Embedded
    private TemplateTitle title;

    @Embedded
    private Shortcut shortcut;

    /**
     * DB 생성 컬럼. 휴지통이면 NULL이 되어 단축어 점유가 풀린다. 값을 직접 넣지 않는다
     */
    @Column(name = "active_shortcut_key", length = 11, insertable = false, updatable = false)
    private String activeShortcutKey;

    @Embedded
    private TemplateContent content;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    /**
     * 생성 시에는 저장 직전에 created_at과 같은 값으로 채운다 (initEditedAt). 이후로는 edit에서만 바뀐다
     */
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

    /**
     * 직접 만든 템플릿
     */
    public static Template create(
            Long userId, TemplateTitle title, Shortcut shortcut, TemplateContent content) {

        require(userId != null, "회원 ID가 없다");
        validateValues(title, shortcut, content);
        return Template.builder()
                .userId(userId)
                .title(title)
                .shortcut(shortcut)
                .content(content)
                .build();
    }

    /**
     * 갤러리 템플릿을 내 템플릿으로 저장한 것. 같은 원본을 여러 번 저장할 수 있다
     */
    public static Template createFromGallery(
            Long userId, Long sourceGalleryTemplateId, TemplateTitle title, Shortcut shortcut, TemplateContent content) {

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

    /**
     * 이름·단축어·본문을 고친다. 최근 수정순 기준인 edited_at을 갱신한다
     */
    public void edit(TemplateTitle title, Shortcut shortcut, TemplateContent content, LocalDateTime now) {
        validateValues(title, shortcut, content);
        require(now != null, "수정 시각이 없다");
        this.title = title;
        this.shortcut = shortcut;
        this.content = content;
        this.editedAt = now;
    }

    /**
     * 복사·삽입 기록. 최근 사용순 기준인 last_used_at만 바꾸고 edited_at은 그대로 둔다
     */
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
