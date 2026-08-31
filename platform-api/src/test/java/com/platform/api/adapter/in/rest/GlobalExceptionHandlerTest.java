package com.platform.api.adapter.in.rest;

import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.exception.IncompleteSubmissionException;
import com.platform.config.domain.exception.SelfApprovalException;
import com.platform.config.domain.exception.SourceConnectionNotFoundException;
import com.platform.config.domain.exception.SubmissionAlreadyExistsException;
import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.exception.InvalidTransitionException;
import com.platform.workflow.domain.exception.ValidationFailedException;
import com.platform.workflow.domain.exception.WorkItemNotFoundException;
import com.platform.workflow.domain.exception.WorkflowConfigNotFoundException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_mapsToNotFoundWithMessage() {
        ProblemDetail pd = handler.handleNotFound(new SubmissionNotFoundException("sub-1"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getDetail()).isEqualTo("Submission not found: sub-1");
    }

    @Test
    void handleNotFound_coversAllRegisteredNotFoundTypes() {
        assertThat(handler.handleNotFound(new ConfigNotFoundException("x")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(handler.handleNotFound(new WorkItemNotFoundException("wi-1")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(handler.handleNotFound(new SourceConnectionNotFoundException("conn-1")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void handleMisconfiguration_hidesInternalDetailAndLogsCorrelationId() {
        MDC.put("correlationId", "corr-abc");
        try {
            ProblemDetail pd = handler.handleMisconfiguration(
                    new WorkflowConfigNotFoundException("TRADE", "tenant-1"));

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            assertThat(pd.getDetail()).isEqualTo("Internal configuration error — contact support");
            assertThat(pd.getProperties()).containsEntry("correlationId", "corr-abc");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void handleConflict_mapsToConflict() {
        ProblemDetail pd = handler.handleConflict(new SubmissionAlreadyExistsException("TRADE"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).contains("TRADE");
    }

    @Test
    void handleOptimisticLock_mapsToConflictWithRetryMessage() {
        ProblemDetail pd = handler.handleOptimisticLock(new OptimisticLockingFailureException("stale"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("Resource was modified concurrently — please retry");
    }

    @Test
    void handleForbidden_coversSelfApprovalAndForbiddenTransition() {
        assertThat(handler.handleForbidden(new SelfApprovalException("user-1")).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(handler.handleForbidden(new ForbiddenTransitionException("not allowed")).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void handleBadRequest_mapsToBadRequest() {
        ProblemDetail pd = handler.handleBadRequest(new IllegalArgumentException("bad field"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("bad field");
    }

    @Test
    void handleUnprocessable_coversAllRegisteredUnprocessableTypes() {
        assertThat(handler.handleUnprocessable(new IncompleteSubmissionException("missing field")).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(handler.handleUnprocessable(new InvalidTransitionException("bad transition")).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(handler.handleUnprocessable(new ValidationFailedException("invalid")).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(handler.handleUnprocessable(new IllegalStateException("bad state")).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
    }

    @Test
    void handleUnexpected_returns500WithGenericDetailAndCorrelationId() {
        MDC.put("correlationId", "corr-xyz");
        try {
            ProblemDetail pd = handler.handleUnexpected(new RuntimeException("boom"));

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            assertThat(pd.getDetail()).isEqualTo("An unexpected error occurred");
            assertThat(pd.getProperties()).containsEntry("correlationId", "corr-xyz");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void handleUnexpected_withoutCorrelationId_omitsProperty() {
        MDC.clear();
        ProblemDetail pd = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(pd.getProperties()).isNull();
    }
}
