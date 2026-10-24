package com.sniptext.global.config;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 응답 시각 형식(명세 공통 규칙 4장). 잭슨 3 기준.
 * <p>
 * 엔티티·DB·JVM이 모두 한국 시간 LocalDateTime이므로 응답 객체에도 LocalDateTime을 그대로 쓰면
 * 여기서 "yyyy-MM-dd'T'HH:mm:ssXXX"(+09:00 포함)로 바꿔 내보낸다. 요청으로 들어오는 시각은 시간대 표시가 필수다.
 */
@Configuration
public class JacksonConfig {

	private static final DateTimeFormatter RESPONSE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

	// 부트가 JacksonModule 빈을 기본 JsonMapper에 자동 등록한다
	@Bean
	public JacksonModule kstDateTimeModule() {
		return new SimpleModule("kst-date-time")
			.addSerializer(LocalDateTime.class, new KstLocalDateTimeSerializer())
			.addSerializer(OffsetDateTime.class, new KstOffsetDateTimeSerializer())
			.addDeserializer(LocalDateTime.class, new OffsetRequiredLocalDateTimeDeserializer());
	}

	/** 한국 시간으로 저장된 값에 +09:00을 붙인다. 초 아래 자리는 버린다. */
	static class KstLocalDateTimeSerializer extends StdSerializer<LocalDateTime> {

		KstLocalDateTimeSerializer() {
			super(LocalDateTime.class);
		}

		@Override
		public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context) {
			generator.writeString(RESPONSE_FORMAT.format(value.atZone(TimeConfig.KST)));
		}

	}

	/** 다른 시간대 값이 와도 같은 순간의 한국 시간으로 바꿔 내보낸다. */
	static class KstOffsetDateTimeSerializer extends StdSerializer<OffsetDateTime> {

		KstOffsetDateTimeSerializer() {
			super(OffsetDateTime.class);
		}

		@Override
		public void serialize(OffsetDateTime value, JsonGenerator generator, SerializationContext context) {
			generator.writeString(RESPONSE_FORMAT.format(value.atZoneSameInstant(TimeConfig.KST)));
		}

	}

	/** 시간대 표시가 있는 시각만 받아 한국 시간으로 바꾼다. 없으면 파싱 실패(COMMON400_1). */
	static class OffsetRequiredLocalDateTimeDeserializer extends StdScalarDeserializer<LocalDateTime> {

		OffsetRequiredLocalDateTimeDeserializer() {
			super(LocalDateTime.class);
		}

		@Override
		public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
			if (!parser.hasToken(JsonToken.VALUE_STRING)) {
				return (LocalDateTime)context.handleUnexpectedToken(LocalDateTime.class, parser);
			}
			String text = parser.getString().trim();
			try {
				return OffsetDateTime.parse(text).atZoneSameInstant(TimeConfig.KST).toLocalDateTime();
			} catch (DateTimeParseException e) {
				throw context.weirdStringException(text, LocalDateTime.class, "시간대 표시가 있는 시각이어야 합니다");
			}
		}

	}

}
