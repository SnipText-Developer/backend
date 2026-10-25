package com.sniptext.domain.user.entity;

import java.time.LocalDateTime;

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

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false, length = 255)
	private String email;

	@Column(name = "nickname", nullable = false, length = 50)
	private String nickname;

	@Column(name = "profile_image_url", length = 500)
	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "template_sort", nullable = false, length = 20)
	private TemplateSort templateSort;

	@Enumerated(EnumType.STRING)
	@Column(name = "plan_override", length = 10)
	private Plan planOverride;

	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Builder
	private User(String email, String nickname, String profileImageUrl, TemplateSort templateSort,
		Plan planOverride, LocalDateTime lastLoginAt, LocalDateTime deletedAt) {
		this.email = email;
		this.nickname = nickname;
		this.profileImageUrl = profileImageUrl;
		this.templateSort = templateSort == null ? TemplateSort.RECENT_USED : templateSort;
		this.planOverride = planOverride;
		this.lastLoginAt = lastLoginAt;
		this.deletedAt = deletedAt;
	}

}
