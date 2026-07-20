package io.github.pgatzka.skymaster.test;

import io.github.pgatzka.skymaster.rest.request.HandshakeRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.annotation.Annotation;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class RequestValidationTest<R> {

    private static Validator validator;

    private static ValidatorFactory factory;

    @BeforeAll
    static void beforeAll() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    protected abstract R valid();

    @Test
    void testValidIsValid() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @AfterAll
    static void afterAll() {
        factory.close();
    }

    private boolean hasViolation(Set<ConstraintViolation<R>> violations, String field, Class<? extends Annotation> constraint) {
        return violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().equals(field) && violation.getConstraintDescriptor().getAnnotation().annotationType() == constraint);
    }

    @ParameterizedTest(name = "Field {2} with {1} value triggers {3}")
    @MethodSource("arguments")
    void validationTests(R request, String description, String field, Class<? extends Annotation> constraint) {
        assertTrue(hasViolation(validator.validate(request), field, constraint));
    }

}
