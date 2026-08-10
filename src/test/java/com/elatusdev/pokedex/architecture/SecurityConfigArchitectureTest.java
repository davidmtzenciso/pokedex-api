// Copyright (c) 2026 ElatusDev
package com.elatusdev.pokedex.architecture;

import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SecurityConfigArchitectureTest {

    private static final String BEAN = "org.springframework.context.annotation.Bean";

    @Test
    void should_terminate_in_any_request_when_the_bean_builds_a_security_filter_chain() {
        methods()
                .that().areAnnotatedWith(BEAN)
                .and().haveRawReturnType(nameMatching(".*\\.SecurityFilterChain"))
                .should(callAnyRequest())
                .because("SB-PA4 — a chain that falls through leaves a route unauthenticated, and no test names the route it forgot")
                .allowEmptyShould(true)
                .check(ProjectClasses.production());
    }

    private static ArchCondition<JavaMethod> callAnyRequest() {
        return new ArchCondition<>("terminate the authorization rules with .anyRequest()") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean terminated = method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> "anyRequest".equals(call.getTarget().getName()));
                events.add(new SimpleConditionEvent(
                        method,
                        terminated,
                        method.getFullName() + (terminated ? " calls" : " never calls") + " anyRequest()"));
            }
        };
    }
}
