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
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
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
 *   <li>{@code POST /data/**} — {@code PLUGIN_ADMIN} (destructive data-reset; not active in production)</li>
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
                    .requestMatchers("/data/**")
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
   * Plain-text password encoder for non-production profiles (dev, docker, default).
   *
   * <p>Scoped to {@code @Profile("!production")} so that exactly one {@link
   * PasswordEncoder} bean is active per profile — in production the {@link
   * #productionPasswordEncoder()} delegating encoder is used instead. Without
   * this scoping both beans would coexist under the {@code production} profile,
   * making the {@code PasswordEncoder} dependency ambiguous.
   *
   * <p>The {@code {noop}} prefix in property files is a convention hint for the
   * migration path, but the raw value is stored because this encoder does not
   * interpret prefixes.
   */
  @Bean
  @Profile("!production")
  public PasswordEncoder passwordEncoder() {
    return NoOpPasswordEncoder.getInstance();
  }

  /**
   * Production-grade password encoder using Spring Security's
   * {@link org.springframework.security.crypto.factory.PasswordEncoderFactories
   * DelegatingPasswordEncoder}.
   *
   * <p>Active only when the {@code production} profile is active. Reads the
   * password prefix ({@code {bcrypt}}, {@code {noop}}, {@code {pbkdf2}}, etc.)
   * from the stored value and delegates to the appropriate encoder. This allows
   * gradual migration from {@code {noop}} plain-text to {@code {bcrypt}} hashes
   * without code changes — only the property values and active profile differ.
   *
   * <p>When the {@code production} profile is NOT active, the
   * {@link #passwordEncoder()} bean (NoOp) is used instead.
   */
  @Bean
  @Profile("production")
  public PasswordEncoder productionPasswordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
