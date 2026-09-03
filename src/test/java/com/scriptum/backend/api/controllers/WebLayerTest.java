package com.scriptum.backend.api.controllers;

import com.scriptum.backend.infrastructure.security.JwtAuthenticationFilter;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@code @WebMvcTest} slice for one controller, with no security in the way.
 *
 * <p>Two things are switched off deliberately:
 *
 * <ul>
 *   <li>the filter chain is not applied, so tests describe routing, JSON binding,
 *       bean validation and exception translation rather than authorization;
 *   <li>{@link JwtAuthenticationFilter} is kept out of the context entirely.
 *       {@code @WebMvcTest} registers {@code Filter} beans, and instantiating that
 *       one drags its collaborators — and through them the JPA infrastructure that
 *       a web slice does not configure — into the context.
 * </ul>
 *
 * <p>Authorization rules are covered by the security integration test, which runs
 * the real chain against the real configuration.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@WebMvcTest(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@interface WebLayerTest {

    /** The controller under test. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
