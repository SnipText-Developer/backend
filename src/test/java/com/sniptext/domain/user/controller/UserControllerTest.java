package com.sniptext.domain.user.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import com.sniptext.domain.user.repository.UserRepository;
import com.sniptext.fixture.UserFixture;
import com.sniptext.support.IntegrationTestSupport;

/** 명세 5번. 실제 토큰 필터와 MySQL까지 거쳐 확인한다. */
@Transactional
class UserControllerTest extends IntegrationTestSupport {

	private static final String PATH = "/api/v1/users/me";
	private static final String EMAIL = "me@gmail.com";

	@Autowired
	private UserRepository userRepository;

	@Test
	@DisplayName("내 정보를 명세의 필드로 내려주고 캐시하지 않는다")
	void getMe() throws Exception {
		Long userId = userRepository.save(UserFixture.user(EMAIL)).getId();

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.isSuccess").value(true))
			.andExpect(jsonPath("$.code").value("USER200_1"))
			.andExpect(jsonPath("$.message").value("내 정보 조회에 성공했습니다."))
			.andExpect(jsonPath("$.result.userId").value(userId))
			.andExpect(jsonPath("$.result.nickname").value(UserFixture.NICKNAME))
			.andExpect(jsonPath("$.result.email").value(EMAIL))
			.andExpect(jsonPath("$.result.profileImageUrl").value(UserFixture.PROFILE_IMAGE_URL))
			.andExpect(jsonPath("$.result.loginType").value("GOOGLE"));
	}

	@Test
	@DisplayName("프로필 이미지가 없으면 profileImageUrl은 null이다")
	void withoutProfileImage() throws Exception {
		Long userId = userRepository.save(UserFixture.userWithoutProfileImage(EMAIL)).getId();

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.profileImageUrl").value(nullValue()));
	}

	@Test
	@DisplayName("?userId로 다른 회원을 지정해도 토큰의 회원 정보만 내려준다")
	void ignoresUserIdParameter() throws Exception {
		Long me = userRepository.save(UserFixture.user(EMAIL)).getId();
		Long other = userRepository.save(UserFixture.user("other@gmail.com")).getId();

		mockMvc.perform(get(PATH).param("userId", String.valueOf(other))
				.header(HttpHeaders.AUTHORIZATION, bearer(me)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.userId").value(me))
			.andExpect(jsonPath("$.result.email").value(EMAIL));
	}

	@Test
	@DisplayName("탈퇴한 회원의 토큰은 USER404_1이다")
	void deletedUser() throws Exception {
		Long userId = userRepository.save(UserFixture.deletedUser(EMAIL, LocalDateTime.of(2026, 11, 1, 10, 0))).getId();

		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(userId)))
			.andExpect(status().isNotFound())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.code").value("USER404_1"))
			.andExpect(jsonPath("$.message").value("회원 정보를 찾을 수 없어요."))
			.andExpect(jsonPath("$.result").value(nullValue()));
	}

	@Test
	@DisplayName("없는 회원의 토큰은 USER404_1이다")
	void unknownUser() throws Exception {
		mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, bearer(Long.MAX_VALUE)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("USER404_1"));
	}

	@Test
	@DisplayName("토큰이 없으면 COMMON401_1이다")
	void withoutToken() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("COMMON401_1"));
	}

}
