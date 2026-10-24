package com.carlosruso.weather.infrastructure.web;

import com.carlosruso.weather.application.port.ProviderRateLimitedException;
import com.carlosruso.weather.application.port.ProviderUnavailableException;
import com.carlosruso.weather.domain.exception.ForecastNotAvailableException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every error is a ProblemDetail (application/problem+json). The base class already maps Spring MVC's
 * own exceptions (missing parameter, wrong type, validation...) to 400. Bodies carry fixed texts:
 * exception messages only go to the log.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** AEMET does not say how long to wait, so this is our own estimate. */
    static final Duration RETRY_AFTER = Duration.ofSeconds(60);

    /**
     * The default detail ("Failed to convert 'unidad' with value: 'KELVIN'") reveals how parameters are
     * converted and echoes the input back. The other 400s from the base class are already neutral.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = ex.getPropertyName() == null
                ? "Invalid parameter value."
                : "Invalid value for parameter '" + ex.getPropertyName() + "'.";
        return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
    }

    /** The default detail ("No static resource api/nada.") repeats the path and reveals Spring's resource handling. */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, "Resource not found.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(ForecastNotAvailableException.class)
    ProblemDetail handleForecastNotAvailable(ForecastNotAvailableException ex) {
        log.debug("Forecast not available: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "There is no forecast for tomorrow for this municipality.");
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    ProblemDetail handleProviderUnavailable(ProviderUnavailableException ex) {
        log.warn("Weather provider unavailable: {}", describe(ex));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The weather provider is not available. Please try again later.");
    }

    @ExceptionHandler(ProviderRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleProviderRateLimited(ProviderRateLimitedException ex) {
        log.warn("Weather provider rate limit reached: {}", describe(ex));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The weather provider is receiving too many requests. Please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER.toSeconds()))
                .body(problem);
    }

    /** Anything else is a bug: full trace in the log, nothing internal in the body. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    /** The message plus its cause's, e.g. "AEMET could not be reached (cause: Connect timed out)". */
    private static String describe(Exception ex) {
        var cause = ex.getCause();
        return cause == null ? ex.getMessage() : ex.getMessage() + " (cause: " + cause.getMessage() + ")";
    }
}
