package com.elatusdev.pokedex.shared.interfaces;

import com.elatusdev.pokedex.contract.dto.FieldErrorDTO;
import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// One RFC 9457 body builder for every context's advice. Each advice used to carry its own
// copy, which is how the type URI, the traceId format and the content type drift apart
// between contexts while every individual test still passes.
public final class ProblemResponses {

    private static final String PROBLEM_BASE = "https://pokedex.elatus-dev.com/problems/";

    private ProblemResponses() {}

    public static ProblemDetailDTO body(HttpStatus status, String code, String slug, String detail) {
        return new ProblemDetailDTO(URI.create(PROBLEM_BASE + slug), status.getReasonPhrase(), status.value(), code)
                .detail(detail)
                .traceId(UUID.randomUUID().toString().replace("-", ""))
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static ResponseEntity<ProblemDetailDTO> respond(ProblemDetailDTO body) {
        return ResponseEntity.status(body.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    public static ResponseEntity<ProblemDetailDTO> respond(
            HttpStatus status, String code, String slug, String detail, List<FieldErrorDTO> errors) {
        return respond(body(status, code, slug, detail).errors(errors));
    }
}
