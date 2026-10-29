package com.sniptext.domain.gallery.entity;

import com.sniptext.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 갤러리 템플릿. 운영 데이터는 DB에 직접 넣으므로 조회만 한다. 공개 데이터라 본문을 암호화하지 않는다.
 * 이름·단축어·본문은 내 템플릿 규칙을 지켜 넣지만, 템플릿의 값 객체는 다른 도메인의 엔티티라 쓰지 않고 문자열로 둔다.
 */
@Getter
@Entity
@Table(name = "gallery_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GalleryTemplate extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 20)
	private GalleryCategory category;

	@Column(name = "title", nullable = false, length = 50)
	private String title;

	/** 기본 단축어 (/ 포함) */
	@Column(name = "shortcut", nullable = false, length = 11)
	private String shortcut;

	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	/** 노출 순서. 같으면 ID 순 */
	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(name = "is_published", nullable = false)
	private boolean published;

	/** S3 랜딩 상세 페이지 주소용 이름. 아직 쓰지 않는다 */
	@Column(name = "slug", length = 100)
	private String slug;

	@Builder(access = AccessLevel.PRIVATE)
	private GalleryTemplate(GalleryCategory category, String title, String shortcut, String content,
		int displayOrder, boolean published) {
		this.category = category;
		this.title = title;
		this.shortcut = shortcut;
		this.content = content;
		this.displayOrder = displayOrder;
		this.published = published;
	}

	/** 테스트 픽스처용. 운영 데이터는 마이그레이션으로 넣는다 */
	public static GalleryTemplate create(GalleryCategory category, String title, String shortcut, String content,
		int displayOrder, boolean published) {
		require(category != null, "카테고리가 없다");
		require(title != null && !title.isEmpty(), "제목이 없다");
		require(shortcut != null && !shortcut.isEmpty(), "단축어가 없다");
		require(content != null && !content.isEmpty(), "본문이 없다");
		return GalleryTemplate.builder()
			.category(category)
			.title(title)
			.shortcut(shortcut)
			.content(content)
			.displayOrder(displayOrder)
			.published(published)
			.build();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
