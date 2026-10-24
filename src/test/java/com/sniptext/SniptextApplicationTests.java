package com.sniptext;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sniptext.support.IntegrationTestSupport;

class SniptextApplicationTests extends IntegrationTestSupport {

	@Autowired
	private JPAQueryFactory queryFactory;

	@Test
	@DisplayName("테스트컨테이너 MySQL로 스프링 컨텍스트가 뜨고 JPAQueryFactory 빈이 등록된다")
	void contextLoads() {
		assertThat(queryFactory).isNotNull();
	}

}
