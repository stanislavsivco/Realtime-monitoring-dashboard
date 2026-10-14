package com.realtime_monitoring_dashboard.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {

    @Test
    void parsesSingleOrigin() {
        assertThat(SecurityConfig.parseOrigins("http://localhost:4200"))
                .containsExactly("http://localhost:4200");
    }

    @Test
    void stripsTrailingSlashAndWhitespace() {
        assertThat(SecurityConfig.parseOrigins("  http://localhost:4200/ "))
                .containsExactly("http://localhost:4200");
    }

    @Test
    void parsesCommaSeparatedOrigins() {
        assertThat(SecurityConfig.parseOrigins("http://localhost:4200, https://rmd.onrender.com/,"))
                .containsExactly("http://localhost:4200", "https://rmd.onrender.com");
    }

    @Test
    void rejectsEmptyValue() {
        assertThatThrownBy(() -> SecurityConfig.parseOrigins(" , "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWildcard() {
        assertThatThrownBy(() -> SecurityConfig.parseOrigins("http://localhost:4200,*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcard");
    }
}
