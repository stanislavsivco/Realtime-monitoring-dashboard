package com.realtime_monitoring_dashboard.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;

class ControllerOperationIdTest {

    private static List<Method> endpoints() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Method> endpoints = new ArrayList<>();
        scanner.findCandidateComponents(ControllerOperationIdTest.class.getPackageName()).forEach(bean -> {
            Class<?> controller = ClassUtils.resolveClassName(bean.getBeanClassName(), null);
            Arrays.stream(controller.getDeclaredMethods())
                    .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                    .forEach(endpoints::add);
        });
        return endpoints;
    }

    @Test
    void everyEndpointDeclaresAnExplicitOperationId() {
        List<Method> endpoints = endpoints();

        assertThat(endpoints).isNotEmpty();
        assertThat(endpoints).allSatisfy(method -> {
            Operation operation = method.getAnnotation(Operation.class);
            assertThat(operation).as(method.toString()).isNotNull();
            assertThat(operation.operationId()).as(method.toString()).isNotBlank();
        });
    }

    @Test
    void operationIdsAreUniqueAcrossControllers() {
        List<String> ids = endpoints().stream().map(method -> method.getAnnotation(Operation.class).operationId()).toList();

        assertThat(ids).doesNotHaveDuplicates();
    }
}
