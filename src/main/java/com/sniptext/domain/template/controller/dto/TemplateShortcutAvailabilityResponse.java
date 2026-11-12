package com.sniptext.domain.template.controller.dto;

import com.sniptext.domain.template.entity.ShortcutUnavailableReason;
import com.sniptext.domain.template.service.dto.result.TemplateShortcutAvailabilityResult;

/**
 * 단축어 확인 결과 (명세 13번). shortcut은 확인한 단축어를 NFC 정규화한 값(대소문자는 입력 그대로)이다.
 * 사용 가능하면 reason은 null로 내보낸다.
 */
public record TemplateShortcutAvailabilityResponse(
	String shortcut,
	boolean available,
	ShortcutUnavailableReason reason
) {

	public static TemplateShortcutAvailabilityResponse from(TemplateShortcutAvailabilityResult result) {
		return new TemplateShortcutAvailabilityResponse(result.shortcut(), result.available(), result.reason());
	}

}
