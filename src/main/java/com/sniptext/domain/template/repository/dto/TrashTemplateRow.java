package com.sniptext.domain.template.repository.dto;

import java.time.LocalDateTime;

/**
 * 휴지통 목록 한 행. 성능 때문이 아니라 본문을 읽지 않기 위한 조회 전용 객체다.
 * 엔티티로 읽으면 내보내지도 않는 본문을 복호화하고, 복호화에 실패한 행 하나가 목록 전체를 500으로 만든다.
 */
public record TrashTemplateRow(Long id, String title, String shortcut, LocalDateTime deletedAt) {
}
