package com.sniptext.domain.template.controller.dto;

import com.sniptext.domain.template.annotation.ValidUsageChannel;
import com.sniptext.domain.template.service.dto.command.TemplateUsageCommand;

/**
 * 템플릿 사용 기록 (명세 14번). 채널은 열거형 대신 문자열로 받아 @ValidUsageChannel로 검사한다
 * (틀린 값이 COMMON400_1이 아니라 TEMPLATE400_9가 되도록).
 */
public record TemplateUsageRequest(
	@ValidUsageChannel String channel
) {

	public TemplateUsageCommand toCommand(Long userId, Long templateId) {
		return new TemplateUsageCommand(userId, templateId);
	}

}
