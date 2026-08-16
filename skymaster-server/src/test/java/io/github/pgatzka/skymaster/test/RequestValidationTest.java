package io.github.pgatzka.skymaster.test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


public abstract class RequestValidationTest<R> {

    private static Validator validator;

    private static ValidatorFactory validatorFactory;

    @BeforeAll
    public static void beforeAll() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    protected abstract R valid();

    @Test
    void testValidIsValid() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @AfterAll
    public static void afterAll() {
        validatorFactory.close();
    }

    private boolean hasViolation(
            Set<ConstraintViolation<R>> violations, String field, Class<? extends Annotation> constraint) {
        return violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(field)
                        && Objects.equals(violation.getConstraintDescriptor().getAnnotation().annotationType(), constraint));
    }


    @ParameterizedTest(name = "Field {2} with value \"{1}\" triggers {3}")
    @MethodSource("arguments")
    void validationTests(R request, String description, String field, Class<? extends Annotation> annotation) {
        assertThat(hasViolation(validator.validate(request), field, annotation)).isTrue();
    }

}
