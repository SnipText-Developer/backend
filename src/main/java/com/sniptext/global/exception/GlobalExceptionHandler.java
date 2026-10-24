package com.sniptext.global.exception;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.sniptext.global.response.ApiResponse;
import com.sniptext.global.response.ResultCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 에러를 공통 응답 형식으로 바꾼다. 에러 응답의 result는 null이고 캐시하지 않는다.
 * <p>
 * 검증 에러는 요청 객체의 검증 어노테이션 message에 적은 결과 코드(예: "TEMPLATE400_1")로 응답한다.
 * 여러 개가 틀리면 필드 선언 순서상 첫 번째 하나만, 코드가 없으면 COMMON400_1.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private final ResultCodeRegistry resultCodeRegistry;

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Object> handleBusinessException(BusinessException e) {
		return errorResponse(e.getResultCode(), new HttpHeaders());
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException e) {
		Optional<String> firstMessage = e.getConstraintViolations().stream()
			.min(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
			.map(ConstraintViolation::getMessage);
		return errorResponse(toValidationCode(firstMessage), new HttpHeaders());
	}

	// 인증·권한 예외는 시큐리티가 COMMON401_1·COMMON403_1로 응답하도록 그대로 넘긴다
	@ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
	public void rethrowSecurityException(RuntimeException e) {
		throw e;
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpected(Exception e, HttpServletRequest request) {
		// 요청 본문·쿼리 문자열에는 개인정보가 있을 수 있어 메서드와 경로만 남긴다
		log.error("처리하지 못한 예외: {} {}", request.getMethod(), request.getRequestURI(), e);
		return errorResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, new HttpHeaders());
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Class<?> targetType = e.getParameter().getParameterType();
		List<MessageSourceResolvable> errors = new ArrayList<>(sortByDeclaration(e.getFieldErrors(), targetType));
		errors.addAll(e.getGlobalErrors());
		return errorResponse(toValidationCode(firstMessage(errors)), new HttpHeaders());
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Optional<String> firstMessage = e.getParameterValidationResults().stream()
			.sorted(Comparator.comparingInt(result -> result.getMethodParameter().getParameterIndex()))
			.map(GlobalExceptionHandler::firstMessage)
			.flatMap(Optional::stream)
			.findFirst();
		return errorResponse(toValidationCode(firstMessage), new HttpHeaders());
	}

	/** 스프링 MVC 표준 예외(JSON 파싱 실패, 타입 불일치, 필수 값 누락, 없는 경로, 메서드 불일치 등). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
		HttpStatusCode statusCode, WebRequest request) {
		if (request instanceof ServletWebRequest servletWebRequest) {
			if (servletWebRequest.getResponse() != null && servletWebRequest.getResponse().isCommitted()) {
				return null;
			}
			if (statusCode.is5xxServerError()) {
				HttpServletRequest servletRequest = servletWebRequest.getRequest();
				log.error("처리하지 못한 예외: {} {}", servletRequest.getMethod(), servletRequest.getRequestURI(), e);
			}
		}
		return errorResponse(toCommonCode(statusCode), headers);
	}

	private ResultCode toValidationCode(Optional<String> message) {
		return message.flatMap(resultCodeRegistry::find).orElse(CommonErrorCode.BAD_REQUEST);
	}

	private static ResultCode toCommonCode(HttpStatusCode statusCode) {
		if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
			return CommonErrorCode.NOT_FOUND;
		}
		if (statusCode.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
			return CommonErrorCode.METHOD_NOT_ALLOWED;
		}
		if (statusCode.is4xxClientError()) {
			return CommonErrorCode.BAD_REQUEST;
		}
		return CommonErrorCode.INTERNAL_SERVER_ERROR;
	}

	private static ResponseEntity<Object> errorResponse(ResultCode resultCode, HttpHeaders headers) {
		HttpHeaders responseHeaders = HttpHeaders.copyOf(headers);
		responseHeaders.setCacheControl(CacheControl.noStore());
		return ResponseEntity.status(resultCode.getHttpStatus())
			.headers(responseHeaders)
			.body(ApiResponse.of(resultCode));
	}

	private static Optional<String> firstMessage(ParameterValidationResult result) {
		List<? extends MessageSourceResolvable> errors = result.getResolvableErrors();
		Class<?> type = result.getMethodParameter().getParameterType();
		List<FieldError> fieldErrors = errors.stream()
			.filter(FieldError.class::isInstance)
			.map(FieldError.class::cast)
			.toList();
		List<MessageSourceResolvable> ordered = new ArrayList<>(sortByDeclaration(fieldErrors, type));
		errors.stream().filter(error -> !(error instanceof FieldError)).forEach(ordered::add);
		return firstMessage(ordered);
	}

	private static Optional<String> firstMessage(List<? extends MessageSourceResolvable> errors) {
		return errors.stream().findFirst().map(MessageSourceResolvable::getDefaultMessage);
	}

	// 검증기가 돌려주는 에러 순서는 보장되지 않으므로 요청 객체의 필드 선언 순서로 정렬한다
	private static List<FieldError> sortByDeclaration(List<FieldError> fieldErrors, Class<?> targetType) {
		List<String> fieldNames = declaredFieldNames(targetType);
		return fieldErrors.stream()
			.sorted(Comparator.comparingInt(error -> {
				int index = fieldNames.indexOf(rootFieldName(error.getField()));
				return index < 0 ? Integer.MAX_VALUE : index;
			}))
			.toList();
	}

	private static List<String> declaredFieldNames(Class<?> type) {
		if (type.isRecord()) {
			return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
		}
		List<String> names = new ArrayList<>();
		for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
			Arrays.stream(current.getDeclaredFields()).map(Field::getName).forEach(names::add);
		}
		return names;
	}

	private static String rootFieldName(String field) {
		int end = field.length();
		int dot = field.indexOf('.');
		int bracket = field.indexOf('[');
		if (dot >= 0) {
			end = dot;
		}
		if (bracket >= 0 && bracket < end) {
			end = bracket;
		}
		return field.substring(0, end);
	}

}
