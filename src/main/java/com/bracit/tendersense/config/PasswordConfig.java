package com.bracit.tendersense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing, and nothing else.
 *
 * <p>Only {@code spring-security-crypto} is on the classpath, so declaring this bean does
 * not drag in a filter chain, an {@code AuthenticationManager} or any auto-configuration to
 * work around. Sign-in is a session attribute set by a controller (see {@code AuthService});
 * this class exists purely so passwords are never stored in the clear.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
