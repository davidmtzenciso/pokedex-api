package com.elatusdev.pokedex.shared.interfaces;

import static org.assertj.core.api.Assertions.assertThat;

import com.elatusdev.pokedex.contract.dto.ProblemDetailDTO;
import com.elatusdev.pokedex.shared.domain.InvalidPaginationException;
import com.elatusdev.pokedex.shared.domain.InvalidPokemonDataException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

class ValidationExceptionHandlerTest {

    private final ValidationExceptionHandler handler = new ValidationExceptionHandler();

    private static ConstraintViolation<?> violation(String path, String message) {
        Path propertyPath = Mockito.mock(Path.class);
        Mockito.when(propertyPath.toString()).thenReturn(path);
        ConstraintViolation<?> violation = Mockito.mock(ConstraintViolation.class);
        Mockito.when(violation.getPropertyPath()).thenReturn(propertyPath);
        Mockito.when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    // §9.5 — a rejected page or size is INVALID_PAGINATION, and it names the cap
    @Test
    void should_answer_invalid_pagination_when_the_rejected_parameter_is_size() {
        ResponseEntity<ProblemDetailDTO> response = handler.onConstraintViolation(
                new ConstraintViolationException(Set.of(violation("listLocalPokemon.size", "must be <= 100"))));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_PAGINATION");
        assertThat(response.getBody().getDetail()).contains("100");
        assertThat(response.getBody().getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getField()).isEqualTo("size");
            assertThat(error.getMessage()).isEqualTo("must be <= 100");
        });
    }

    @Test
    void should_answer_invalid_pagination_when_the_rejected_parameter_is_page() {
        ResponseEntity<ProblemDetailDTO> response = handler.onConstraintViolation(
                new ConstraintViolationException(Set.of(violation("listLocalPokemon.page", "must be >= 0"))));

        assertThat(response.getBody().getCode()).isEqualTo("INVALID_PAGINATION");
    }

    // the bug this split exists to prevent: an id below 1 is not a pagination problem, and
    // answering INVALID_PAGINATION tells the caller to fix a page size they never sent
    @Test
    void should_answer_validation_error_when_the_rejected_parameter_is_not_pagination() {
        ResponseEntity<ProblemDetailDTO> response = handler.onConstraintViolation(
                new ConstraintViolationException(Set.of(violation("getLocalPokemon.id", "must be >= 1"))));

        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getErrors()).singleElement().satisfies(error -> assertThat(error.getField())
                .isEqualTo("id"));
    }

    @Test
    void should_report_a_property_path_without_a_prefix_unchanged() {
        ResponseEntity<ProblemDetailDTO> response = handler.onConstraintViolation(
                new ConstraintViolationException(Set.of(violation("tag", "must not be blank"))));

        assertThat(response.getBody().getErrors()).singleElement().satisfies(error -> assertThat(error.getField())
                .isEqualTo("tag"));
    }

    // AC-US04-3 — the 400 the story names, and it has to say which field
    @Test
    void should_name_the_offending_field_when_the_body_fails_validation() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "createLocalPokemonRequestDTO");
        binding.rejectValue(null, "Min", "must be greater than 0");
        binding.addError(new org.springframework.validation.FieldError(
                "createLocalPokemonRequestDTO", "massHectograms", "must be greater than 0"));
        MethodParameter parameter =
                new MethodParameter(ValidationExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0);

        ResponseEntity<ProblemDetailDTO> response =
                handler.onBodyValidation(new MethodArgumentNotValidException(parameter, binding));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getErrors())
                .extracting(e -> e.getField())
                .contains("massHectograms");
    }

    @Test
    void should_answer_invalid_pagination_for_the_domain_exception() {
        ResponseEntity<ProblemDetailDTO> response =
                handler.onInvalidPagination(new InvalidPaginationException("size must be between 1 and 100", 0, 500));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_PAGINATION");
    }

    @Test
    void should_answer_validation_error_for_invalid_domain_data() {
        ResponseEntity<ProblemDetailDTO> response =
                handler.onInvalidDomainData(new InvalidPokemonDataException("mass must be positive"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getDetail()).isEqualTo("mass must be positive");
    }

    // IA9 — the second of the two parameter-validation types. Mapping only one returns 500
    // for half of all validation failures.
    @Test
    void should_map_the_method_validation_type_as_well_as_the_constraint_type() throws Exception {
        MethodParameter parameter =
                new MethodParameter(ValidationExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult("101", "size");
        binding.reject("Max", "must be less than or equal to 100");
        HandlerMethodValidationException rejected = Mockito.mock(HandlerMethodValidationException.class);
        Mockito.when(rejected.getParameterValidationResults())
                .thenReturn(List.of(new ParameterValidationResult(
                        parameter, "101", binding.getAllErrors(), null, null, null, (e, t) -> null)));

        ResponseEntity<ProblemDetailDTO> response = handler.onMethodValidation(rejected);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrors()).isNotEmpty();
    }

    // the annotation carries the name the caller used, and it wins over the compiled
    // parameter name — a @RequestParam("size") bound to a variable called "pageSize" must
    // still report "size" in errors[], because that is what the caller sent
    @Test
    void should_prefer_the_request_parameter_name_over_the_compiled_one() throws Exception {
        ResponseEntity<ProblemDetailDTO> response = handler.onMethodValidation(
                methodValidationOn(ValidationExceptionHandlerTest.class.getDeclaredMethod("annotatedQuery", Integer.class)));

        assertThat(response.getBody().getCode()).isEqualTo("INVALID_PAGINATION");
        assertThat(response.getBody().getErrors()).singleElement().satisfies(error -> assertThat(error.getField())
                .isEqualTo("size"));
    }

    @Test
    void should_fall_back_to_the_path_variable_name() throws Exception {
        ResponseEntity<ProblemDetailDTO> response = handler.onMethodValidation(
                methodValidationOn(ValidationExceptionHandlerTest.class.getDeclaredMethod("annotatedPath", Long.class)));

        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getErrors()).singleElement().satisfies(error -> assertThat(error.getField())
                .isEqualTo("id"));
    }

    private static HandlerMethodValidationException methodValidationOn(java.lang.reflect.Method method) {
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult("bad", "value");
        binding.reject("Invalid", "is not acceptable");
        HandlerMethodValidationException rejected = Mockito.mock(HandlerMethodValidationException.class);
        Mockito.when(rejected.getParameterValidationResults())
                .thenReturn(List.of(new ParameterValidationResult(
                        parameter, "bad", binding.getAllErrors(), null, null, null, (e, t) -> null)));
        return rejected;
    }

    @SuppressWarnings("unused")
    private void annotatedQuery(@org.springframework.web.bind.annotation.RequestParam("size") Integer pageSize) {
        // the compiled name and the wire name differ on purpose
    }

    @SuppressWarnings("unused")
    private void annotatedPath(@org.springframework.web.bind.annotation.PathVariable("id") Long recordId) {
        // same, for a path variable
    }

    @SuppressWarnings("unused")
    private void dummy(String size) {
        // a method with a named parameter, so MethodParameter has something real to resolve
    }
}
