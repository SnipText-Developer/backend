package com.sniptext.global.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 생성 시각과 수정 시각을 기록하는 엔티티의 부모. 저장 시 두 값은 같다.
 * 대량 갱신 쿼리(@Modifying)는 감사 기능을 거치지 않으므로 updated_at을 직접 넣어야 한다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity {

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

}
