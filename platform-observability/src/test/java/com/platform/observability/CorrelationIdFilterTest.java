package com.platform.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void inbound_correlation_id_is_put_in_mdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "test-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, captureAndAssert("test-correlation-id"));
    }

    @Test
    void missing_header_generates_uuid_correlation_id() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            assertThat(correlationId).isNotBlank().matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        });
    }

    @Test
    void correlation_id_is_added_to_response_header() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "resp-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("resp-id");
    }

    @Test
    void mdc_is_cleared_after_filter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "cleanup-test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private FilterChain captureAndAssert(String expected) {
        return (req, res) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(expected);
    }
}
