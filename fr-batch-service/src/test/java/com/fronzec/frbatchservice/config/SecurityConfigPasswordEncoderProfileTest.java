/* 2024-2026 */
package com.fronzec.frbatchservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifies that exactly one {@link PasswordEncoder} bean is active per profile.
 *
 * <p>Regression guard for the production ambiguity bug: the NoOp encoder bean
 * ({@code @Profile("!production")}) and the delegating bcrypt encoder bean
 * ({@code @Profile("production")}) must never coexist. If they did, the
 * {@code PasswordEncoder} dependency would be ambiguous under the production
 * profile.
 *
 * <p>Loads only the {@link SecurityConfig} web slice via {@link
 * WebApplicationContextRunner} — no datasource, JPA, or batch infrastructure —
 * so the production profile can be exercised without a real MySQL instance.
 */
class SecurityConfigPasswordEncoderProfileTest {

  // SecurityConfig is annotated with @EnableWebSecurity, which imports the
  // HttpSecurity infrastructure — no SecurityAutoConfiguration needed here.
  private WebApplicationContextRunner runnerWithProfiles(String... profiles) {
    return new WebApplicationContextRunner()
        .withUserConfiguration(SecurityConfig.class)
        .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profiles));
  }

  @Test
  void productionProfile_hasSingleDelegatingBcryptEncoder() {
    runnerWithProfiles("production")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(PasswordEncoder.class))
                  .as("exactly one PasswordEncoder bean must be active under the production profile")
                  .hasSize(1);

              PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
              // DelegatingPasswordEncoder prefixes encoded values with the default id.
              assertThat(encoder.encode("s3cret"))
                  .as("production encoder must be the delegating bcrypt encoder")
                  .startsWith("{bcrypt}");
              assertThat(encoder.matches("s3cret", encoder.encode("s3cret"))).isTrue();
            });
  }

  @Test
  void nonProductionProfile_hasSingleNoOpEncoder() {
    runnerWithProfiles("docker")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(PasswordEncoder.class))
                  .as("exactly one PasswordEncoder bean must be active under a non-production profile")
                  .hasSize(1);

              PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
              // NoOpPasswordEncoder stores/compares the raw value with no prefix.
              assertThat(encoder.encode("s3cret"))
                  .as("non-production encoder must be the NoOp encoder")
                  .isEqualTo("s3cret");
              assertThat(encoder.matches("s3cret", "s3cret")).isTrue();
            });
  }

  @Test
  void defaultProfile_hasSingleNoOpEncoder() {
    // No profile active at all — the !production NoOp bean must still be the only one.
    new WebApplicationContextRunner()
        .withUserConfiguration(SecurityConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeansOfType(PasswordEncoder.class)).hasSize(1);
              assertThat(context.getBean(PasswordEncoder.class).encode("s3cret")).isEqualTo("s3cret");
            });
  }
}
