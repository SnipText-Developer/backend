package com.sniptext.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.sniptext.TestcontainersConfiguration;
import com.sniptext.global.security.JwtTokenProvider;

/**
 * 통합 테스트 공통 설정. 설정이 같으면 스프링 컨텍스트와 MySQL 컨테이너를 테스트 클래스끼리 공유한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestApiController.class, TestExceptionController.class,
	TestTimeController.class})
public abstract class IntegrationTestSupport {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	/**
	 * 회원 번호로 실제 접근 토큰을 발급해 Authorization 헤더 값을 만든다 (필터까지 거치는 진짜 인증 흐름).
	 * 예: {@code mockMvc.perform(get(...).header(HttpHeaders.AUTHORIZATION, bearer(1L)))}
	 */
	protected String bearer(Long userId) {
		return "Bearer " + jwtTokenProvider.issueAccessToken(userId);
	}

}
