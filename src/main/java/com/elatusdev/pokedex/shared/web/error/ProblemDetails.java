package com.elatusdev.pokedex.shared.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

// RFC 9457 in the filter chain. A 401 raised here never reaches @RestControllerAdvice — the
// request is rejected before a handler is selected — so the body has to be written by hand
// to keep the error contract identical on both sides of the chain.
public final class ProblemDetails {

    static final String TYPE_PREFIX = "https://pokedex.elatus-dev.com/problems/";

    private ProblemDetails() {}

    public static ProblemDetail of(HttpStatus status, String title, String detail, String code, String instance) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setInstance(URI.create(instance));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write(
                        """
                        {"type":"%s%s","title":"%s","status":%d,"detail":"%s","instance":"%s","code":"%s","timestamp":"%s"}"""
                                .formatted(
                                        TYPE_PREFIX,
                                        code.toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
                                        title,
                                        status.value(),
                                        detail,
                                        request.getRequestURI(),
                                        code,
                                        Instant.now()));
    }
}
