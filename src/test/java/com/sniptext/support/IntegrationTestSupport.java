package com.sniptext.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.sniptext.TestcontainersConfiguration;

/**
 * 통합 테스트 공통 설정. 설정이 같으면 스프링 컨텍스트와 MySQL 컨테이너를 테스트 클래스끼리 공유한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestApiController.class})
public abstract class IntegrationTestSupport {

	@Autowired
	protected MockMvc mockMvc;

}
