package ai.ghostshift.api;

import ai.ghostshift.domain.ApiError;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One consistent JSON error contract. Framework defaults are replaced so users never
 * receive stack traces, and every failure carries a request identifier for local logs.
 */
@Controller
public class ErrorController {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorController.class);

    @Error(global = true, exception = ApiException.class)
    public HttpResponse<ApiError> apiException(HttpRequest<?> request, ApiException exception) {
        String requestId = requestId(request);
        LOG.info("requestId={} {} {} -> {} {}", requestId, request.getMethod(), request.getPath(),
                exception.status(), exception.code());
        return HttpResponse.<ApiError>status(exception.status())
                .header("X-Request-Id", requestId)
                .body(exception.toError(requestId));
    }

    @Error(global = true, exception = ConstraintViolationException.class)
    public HttpResponse<ApiError> validation(HttpRequest<?> request, ConstraintViolationException exception) {
        String requestId = requestId(request);
        Map<String, Object> details = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            details.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        ApiError error = new ApiError("VALIDATION_FAILED", "Some fields need attention.", details,
                requestId, java.time.Instant.now().toString());
        return HttpResponse.<ApiError>status(HttpStatus.BAD_REQUEST)
                .header("X-Request-Id", requestId)
                .body(error);
    }

    @Error(global = true, exception = HttpStatusException.class)
    public HttpResponse<ApiError> httpStatus(HttpRequest<?> request, HttpStatusException exception) {
        String requestId = requestId(request);
        int status = exception.getStatus().getCode();
        String code = status == 404 ? "NOT_FOUND" : status == 405 ? "METHOD_NOT_ALLOWED" : "HTTP_ERROR";
        String message = status == 404
                ? "The requested endpoint or resource does not exist."
                : status == 405
                ? "That HTTP method is not supported here."
                : "The request could not be completed.";
        ApiError error = new ApiError(code, message, Map.of(), requestId, java.time.Instant.now().toString());
        return HttpResponse.<ApiError>status(status).header("X-Request-Id", requestId).body(error);
    }

    @Error(global = true)
    public HttpResponse<ApiError> unexpected(HttpRequest<?> request, Throwable exception) {
        String requestId = requestId(request);
        if (exception instanceof IllegalArgumentException || exception.getClass().getSimpleName().contains("Json")) {
            LOG.info("requestId={} invalid request payload: {}", requestId, exception.getMessage());
            ApiError error = new ApiError("INVALID_REQUEST", "The request payload could not be read.",
                    Map.of(), requestId, java.time.Instant.now().toString());
            return HttpResponse.<ApiError>status(HttpStatus.BAD_REQUEST)
                    .header("X-Request-Id", requestId).body(error);
        }
        LOG.error("requestId={} unhandled failure on {} {}", requestId, request.getMethod(), request.getPath(), exception);
        ApiError error = new ApiError("INTERNAL_ERROR",
                "GhostShift hit an unexpected local error. Check the backend log with this request id.",
                Map.of(), requestId, java.time.Instant.now().toString());
        return HttpResponse.<ApiError>status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Request-Id", requestId).body(error);
    }

    private static String requestId(HttpRequest<?> request) {
        String supplied = request.getHeaders().get("X-Request-Id");
        if (supplied != null && supplied.matches("[A-Za-z0-9._-]{1,64}")) {
            return supplied;
        }
        return ApiException.newRequestId();
    }
}
