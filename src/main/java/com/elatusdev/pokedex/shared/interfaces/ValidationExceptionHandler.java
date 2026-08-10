package com.elatusdev.pokedex.shared.interfaces;

import com.elatusdev.pokedex.contract.dto.FieldErrorDTO;
import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import com.elatusdev.pokedex.shared.domain.InvalidPaginationException;
import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

// Every cross-cutting validation failure, in one place. These types belong to Spring or to
// shared, so this advice depends on no context and BC3 holds — and because exactly one
// advice claims each of them, the ordering problem cannot arise. Two advices claiming one
// exception type is resolved by advice ORDERING rather than specificity, so the broader one
// silently wins; that is how INVALID_PAGINATION disappeared once already.
@RestControllerAdvice
public class ValidationExceptionHandler {

    // page and size are the only parameters whose failure is a pagination problem. Every
    // other rejected parameter — an id below 1, a tag longer than the cap — is an ordinary
    // validation error, and answering INVALID_PAGINATION for those tells the caller to fix
    // a page size they never sent.
    private static final Set<String> PAGINATION_PARAMETERS = Set.of("page", "size");

    // IA9 — parameter validation on a generated *Api interface surfaces as TWO exception
    // types. Mapping only one returns 500 for half of all validation failures, and which
    // half depends on whether the @Validated proxy or MVC method validation fires.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetailDTO> onMethodValidation(HandlerMethodValidationException rejected) {
        // IA8 — renamed from getAllValidationResults() in Framework 7
        List<FieldErrorDTO> errors = rejected.getParameterValidationResults().stream()
                .map(result -> new FieldErrorDTO(
                        nameOf(result.getMethodParameter()),
                        result.getResolvableErrors().getFirst().getDefaultMessage()))
                .toList();
        return respondToParameterFailure(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetailDTO> onConstraintViolation(ConstraintViolationException rejected) {
        List<FieldErrorDTO> errors = rejected.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDTO(lastSegmentOf(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();
        return respondToParameterFailure(errors);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ProblemDetailDTO> onInvalidPagination(InvalidPaginationException rejected) {
        return ProblemResponses.respond(ProblemResponses.body(
                HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "invalid-pagination", rejected.getMessage()));
    }

    // AC-US04-3 — the 400 the story names explicitly, and it has to name the offending
    // field: "your payload is wrong" without saying which part is not actionable.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailDTO> onBodyValidation(MethodArgumentNotValidException rejected) {
        List<FieldErrorDTO> errors = rejected.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDTO(error.getField(), error.getDefaultMessage()))
                .toList();
        return ProblemResponses.respond(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "validation-error", "The request failed validation", errors);
    }

    @ExceptionHandler(InvalidPokemonDataException.class)
    public ResponseEntity<ProblemDetailDTO> onInvalidDomainData(InvalidPokemonDataException rejected) {
        return ProblemResponses.respond(ProblemResponses.body(
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "validation-error", rejected.getMessage()));
    }

    private static ResponseEntity<ProblemDetailDTO> respondToParameterFailure(List<FieldErrorDTO> errors) {
        boolean pagination = errors.stream().anyMatch(error -> PAGINATION_PARAMETERS.contains(error.getField()));
        return pagination
                ? ProblemResponses.respond(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PAGINATION",
                        "invalid-pagination",
                        "page must be >= 0 and size must be between 1 and 100",
                        errors)
                : ProblemResponses.respond(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "validation-error",
                        "The request failed validation",
                        errors);
    }

    // getParameterName() is null unless the whole call chain was compiled with -parameters,
    // and the generated *Api interface is not ours to guarantee that for. The binding
    // annotation carries the name the caller actually used, so it is the reliable source —
    // without this, errors[] names every field "null" and the pagination check never matches.
    private static String nameOf(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.value().isEmpty()) {
            return requestParam.value();
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.value().isEmpty()) {
            return pathVariable.value();
        }
        return parameter.getParameterName();
    }

    // a violation path reads like "listLocalPokemon.size"; the caller sent "size"
    private static String lastSegmentOf(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }
}
