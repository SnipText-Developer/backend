package com.sniptext.global.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DevTokenRequest(
	@NotNull @Positive Long userId
) {
}
