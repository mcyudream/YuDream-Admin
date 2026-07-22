package online.yudream.base.interfaces.common.interceptor;

import online.yudream.base.application.system.monitor.service.SystemMonitorAppService;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.interfaces.common.RequestFailureContext;
import online.yudream.base.interfaces.common.config.WebLogProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebInvokeTimeInterceptorTest {

    @Test
    void recordsHandledFailureAndMasksSensitiveQuery() {
        SystemMonitorAppService monitorService = mock(SystemMonitorAppService.class);
        WebInvokeTimeInterceptor interceptor = new WebInvokeTimeInterceptor(properties(), monitorService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        request.setQueryString("token=abc&name=test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestFailureContext.mark(request, new IllegalArgumentException());

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<ApiLogDTO> captor = ArgumentCaptor.forClass(ApiLogDTO.class);
        verify(monitorService).recordApiLog(captor.capture());
        ApiLogDTO apiLog = captor.getValue();
        assertThat(apiLog.getSuccess()).isFalse();
        assertThat(apiLog.getErrorMessage()).isEqualTo("IllegalArgumentException");
        assertThat(apiLog.getQuery()).isEqualTo("token=******&name=test");
    }

    @Test
    void suppressesAuditPersistenceFailure() {
        SystemMonitorAppService monitorService = mock(SystemMonitorAppService.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(monitorService).recordApiLog(any(ApiLogDTO.class));
        WebInvokeTimeInterceptor interceptor = new WebInvokeTimeInterceptor(properties(), monitorService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertThatCode(() -> interceptor.afterCompletion(request, response, new Object(), null))
                .doesNotThrowAnyException();
    }

    private WebLogProperties properties() {
        WebLogProperties properties = new WebLogProperties();
        properties.setEnabled(true);
        return properties;
    }
}
