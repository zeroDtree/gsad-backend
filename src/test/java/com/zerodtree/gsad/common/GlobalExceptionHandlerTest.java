package com.zerodtree.gsad.common;

import com.zerodtree.gsad.security.LoginRateLimitErrorData;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusiness_includesRateLimitData() {
        LoginRateLimitErrorData data = new LoginRateLimitErrorData(0, 12);
        ResponseEntity<ApiResponse<Object>> response = handler.handleBusiness(
                new BusinessException(ErrorCode.RATE_LIMITED, "Too many login attempts. Try again in 12 minutes.", data));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("RATE_LIMITED");
        assertThat(response.getBody().getData()).isEqualTo(data);
    }

    @Test
    void handleGeneral_returnsGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneral(new RuntimeException("database connection leaked"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Internal server error");
    }
}
