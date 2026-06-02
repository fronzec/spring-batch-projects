/* 2024-2025 */
package com.fronzec.frbatchservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security baseline for the plugin-management REST API.
 *
 * <p>Disabled in the {@code test} profile so 88+ existing integration/unit tests
 * continue to run without authentication. Security-specific integration tests use
 * {@code @WebMvcTest} with {@code @WithMockUser} on a non-test profile context.
 *
 * <h3>Authorization rules</h3>
 * <ul>
 *   <li>{@code GET /jobs/plugins} — public (plugin discovery)</li>
 *   <li>{@code GET /jobs/**} — {@code PLUGIN_VIEWER} or {@code PLUGIN_ADMIN}</li>
 *   <li>{@code POST /jobs/**}, {@code PUT /jobs/**}, {@code DELETE /jobs/**} — {@code PLUGIN_ADMIN}</li>
 *   <li>{@code /actuator/health} — public</li>
 * </ul>
 *
 * <h3>Wire protocol</h3>
 * HTTP Basic authentication over a stateless session, with CSRF disabled (REST
 * API consumed by machines, not browsers).
 */
@Configuration
@EnableWebSecurity
@Profile("!test")
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

  private final SecurityProperties securityProperties;

  public SecurityConfig(SecurityProperties securityProperties) {
    this.securityProperties = securityProperties;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/jobs/plugins")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/jobs/**")
                    .hasAnyRole("PLUGIN_VIEWER", "PLUGIN_ADMIN")
                    .requestMatchers("/jobs/**")
                    .hasRole("PLUGIN_ADMIN")
                    .anyRequest()
                    .authenticated())
        .httpBasic(Customizer.withDefaults());

    return http.build();
  }

  @Bean
  public UserDetailsService userDetailsService() {
    UserDetails admin =
        User.withUsername(securityProperties.getAdminUser())
            .password(securityProperties.getAdminPassword())
            .roles("PLUGIN_ADMIN")
            .build();

    UserDetails viewer =
        User.withUsername(securityProperties.getViewerUser())
            .password(securityProperties.getViewerPassword())
            .roles("PLUGIN_VIEWER")
            .build();

    return new InMemoryUserDetailsManager(admin, viewer);
  }

  /**
   * Plain-text password encoder for development.
   *
   * <p>Replace with {@code BCryptPasswordEncoder} and {@code {bcrypt}} hashes in
   * production. The {@code {noop}} prefix in property files is a convention hint
   * for the migration path, but the raw value is stored because this encoder
   * does not interpret prefixes.
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }
}
