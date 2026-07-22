package online.yudream.base.interfaces.exception;

import jakarta.servlet.http.HttpServletRequest;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.common.RequestFailureContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessExceptionToBadRequestAndRetainsResult() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = handler.handleBizException(request, new BizException(1200, "业务失败"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).extracting("code", "message").containsExactly(1200, "业务失败");
        assertThat(RequestFailureContext.getSummary(request)).isEqualTo("BizException");
    }

    @Test
    void mapsUnknownExceptionToInternalServerErrorWithoutLeakingMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = handler.handleException(request, new IllegalStateException("secret-token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).extracting("code", "message").containsExactly(500, "系统内部错误");
        assertThat(RequestFailureContext.getSummary(request)).isEqualTo("IllegalStateException");
    }
}
