package com.platform.api.adapter.in.rest;

import com.platform.config.domain.exception.ConfigNotFoundException;
import com.platform.config.domain.exception.IncompleteSubmissionException;
import com.platform.config.domain.exception.SelfApprovalException;
import com.platform.config.domain.exception.SubmissionAlreadyExistsException;
import com.platform.config.domain.exception.SubmissionNotFoundException;
import com.platform.workflow.domain.exception.ForbiddenTransitionException;
import com.platform.workflow.domain.exception.InvalidTransitionException;
import com.platform.workflow.domain.exception.ValidationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({SubmissionNotFoundException.class, ConfigNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SubmissionAlreadyExistsException.class)
    public ProblemDetail handleConflict(SubmissionAlreadyExistsException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Resource was modified concurrently — please retry");
    }

    @ExceptionHandler({SelfApprovalException.class, ForbiddenTransitionException.class})
    public ProblemDetail handleForbidden(RuntimeException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({IncompleteSubmissionException.class, InvalidTransitionException.class,
                       ValidationFailedException.class, IllegalStateException.class})
    public ProblemDetail handleUnprocessable(RuntimeException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        String correlationId = MDC.get("correlationId");
        log.error("correlationId={} Unhandled exception", correlationId, ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setDetail("An unexpected error occurred");
        if (correlationId != null) pd.setProperty("correlationId", correlationId);
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setDetail(detail);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) pd.setProperty("correlationId", correlationId);
        return pd;
    }
}
