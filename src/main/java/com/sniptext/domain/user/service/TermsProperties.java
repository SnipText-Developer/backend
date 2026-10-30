package com.sniptext.domain.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.sniptext.domain.user.entity.UserTermsAgreement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 가입 때 동의받는 약관의 현재 버전 (sniptext.terms.current-version). 약관이 바뀌면 이 값만 올린다.
 * 비었거나 약관 동의 버전 컬럼(20자)보다 길면 기동에 실패한다.
 */
@Validated
@ConfigurationProperties("sniptext.terms")
public record TermsProperties(
	@NotBlank
	@Size(max = UserTermsAgreement.VERSION_MAX_LENGTH)
	String currentVersion
) {
}
