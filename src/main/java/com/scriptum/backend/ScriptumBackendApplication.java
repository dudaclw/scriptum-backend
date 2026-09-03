package com.scriptum.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * <p>JPA repositories are picked up by {@code JpaRepositoriesAutoConfiguration},
 * which scans this package. Declaring {@code @EnableJpaRepositories} here as well
 * would be redundant, and worse: it is a direct {@code @Import} rather than an
 * auto-configuration, so sliced tests such as {@code @WebMvcTest} cannot switch it
 * off and end up demanding an EntityManagerFactory they never configure.
 */
@SpringBootApplication
public class ScriptumBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScriptumBackendApplication.class, args);
    }

}
