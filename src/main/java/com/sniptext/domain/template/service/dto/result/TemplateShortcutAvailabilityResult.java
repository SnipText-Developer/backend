package com.sniptext.domain.template.service.dto.result;

import com.sniptext.domain.template.entity.ShortcutUnavailableReason;

/** 단축어 확인 결과. shortcut은 NFC 정규화한 값이고, 사용 가능하면 reason은 null이다. */
public record TemplateShortcutAvailabilityResult(
	String shortcut,
	boolean available,
	ShortcutUnavailableReason reason
) {

	public static TemplateShortcutAvailabilityResult available(String shortcut) {
		return new TemplateShortcutAvailabilityResult(shortcut, true, null);
	}

	public static TemplateShortcutAvailabilityResult unavailable(String shortcut, ShortcutUnavailableReason reason) {
		return new TemplateShortcutAvailabilityResult(shortcut, false, reason);
	}

}
