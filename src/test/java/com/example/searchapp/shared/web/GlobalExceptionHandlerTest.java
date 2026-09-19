package com.example.searchapp.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

  @Test
  void reportsValidationErrorsWithJsonFieldNames() throws Exception {
    var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(
        new FieldError("request", "preferredContactMethod", "must not be blank"));
    Method method = getClass().getDeclaredMethod("validationTarget", Object.class);
    var exception =
        new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    var request = new MockHttpServletRequest("POST", "/clients");

    var response =
        new GlobalExceptionHandler()
            .handleMethodArgumentNotValid(
                exception,
                new HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                new ServletWebRequest(request));

    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
    var problem = (ProblemDetail) response.getBody();
    assertThat(problem.getProperties())
        .containsEntry("errors", Map.of("preferred_contact_method", "must not be blank"));
  }

  @SuppressWarnings("unused")
  private void validationTarget(Object request) {}
}
