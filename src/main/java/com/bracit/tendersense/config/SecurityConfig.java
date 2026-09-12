package com.bracit.tendersense.config;

import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.security.AccountStatusFilter;
import com.bracit.tendersense.security.AccountUserDetailsService;
import com.bracit.tendersense.security.ProblemResponses;
import com.bracit.tendersense.security.SpaCsrfTokenRequestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Who may call what.
 *
 * <p>Session sign-in with a cookie -- no JWT, nothing stored client-side. The browser holds
 * a same-origin session cookie; writes also carry the {@code XSRF-TOKEN} cookie back as an
 * {@code X-XSRF-TOKEN} header, which Angular does by itself.
 *
 * <p>Roles:
 * <ul>
 *   <li><b>USER</b> -- a company account: the company screens, scoped to that company.</li>
 *   <li><b>ADMIN</b> -- TenderSense staff: the admin panel and the collection pipeline.</li>
 * </ul>
 * Two pipeline endpoints are the company's own business and stay USER: the morning digest,
 * and rescoring its own tenders after a profile edit.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        // Readable by JavaScript on purpose: the SPA has to echo it in a header.
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** New session id and a fresh CSRF token at sign-in, so neither can be carried across it. */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CookieCsrfTokenRepository csrfTokenRepository) {
        CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(csrfTokenRepository);
        // Writes the new token into the sign-in response itself, so the SPA holds a valid
        // one the moment sign-in returns -- not only after its next request.
        csrf.setRequestHandler(new SpaCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), csrf));
    }

    @Bean
    public AuthenticationManager authenticationManager(AccountUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** Only for `ng serve` on :4200 when its proxy is bypassed; the packaged build is one origin. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("http://localhost:4200"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CookieCsrfTokenRepository csrfTokenRepository,
                                           SecurityContextRepository securityContextRepository,
                                           AccountRepository accountRepository) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .cors(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // An API has nothing to "return to" after sign-in; saving requests only
                // creates sessions for anonymous callers.
                .requestCache(RequestCacheConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/signup", "/api/auth/logout")
                        .permitAll()
                        // The sign-up form lists sectors before an account exists.
                        .requestMatchers(HttpMethod.GET, "/api/sectors").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/pipeline/digest", "/api/pipeline/rescore").hasRole("USER")
                        .requestMatchers("/api/pipeline/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasRole("USER")
                        // The Angular app and its assets.
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                ProblemResponses.write(response, HttpStatus.UNAUTHORIZED, "Not signed in"))
                        .accessDeniedHandler((request, response, e) ->
                                ProblemResponses.write(response, HttpStatus.FORBIDDEN, e instanceof CsrfException
                                        ? "Your session's security token is missing or expired. Reload the page and try again."
                                        : "You do not have access to this.")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        // Always 204: whether there was a session to end is not the caller's
                        // business, and cleaning up after an expired one must not error.
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .addFilterBefore(new AccountStatusFilter(accountRepository, securityContextRepository),
                        AuthorizationFilter.class);
        return http.build();
    }
}
