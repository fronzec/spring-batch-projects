/* 2024-2025 */
package com.fronzec.frbatchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised security credentials bound from {@code app.security.*} properties.
 *
 * <p>Passwords have NO Java-level default. Each active profile must supply its own credentials:
 * <ul>
 *   <li>{@code local} / {@code docker}: plain-text {@code admin123} / {@code viewer123}</li>
 *   <li>{@code production}: bcrypt hashes via env vars {@code APP_SECURITY_ADMIN_PASSWORD}
 *       and {@code APP_SECURITY_VIEWER_PASSWORD}</li>
 * </ul>
 *
 * <p>This is deliberate: a missing password is fail-safe. With no active profile (a
 * misconfiguration) the password is {@code null}, {@link SecurityConfig} fails to build the
 * {@code UserDetails} ({@code password cannot be null}), and the app refuses to start rather
 * than booting with a known insecure default. Tests that load {@link SecurityConfig} in
 * isolation must supply explicit credentials via the context runner's property values.
 */
@ConfigurationProperties("app.security")
public class SecurityProperties {

  /** Username for the admin role (PLUGIN_ADMIN). Defaults to {@code "admin"}. */
  private String adminUser = "admin";

  /** Password for the admin user. No default — supplied per active profile (fail-safe if absent). */
  private String adminPassword;

  /** Username for the viewer role (PLUGIN_VIEWER). Defaults to {@code "viewer"}. */
  private String viewerUser = "viewer";

  /** Password for the viewer user. No default — supplied per active profile (fail-safe if absent). */
  private String viewerPassword;

  public String getAdminUser() {
    return adminUser;
  }

  public void setAdminUser(String adminUser) {
    this.adminUser = adminUser;
  }

  public String getAdminPassword() {
    return adminPassword;
  }

  public void setAdminPassword(String adminPassword) {
    this.adminPassword = adminPassword;
  }

  public String getViewerUser() {
    return viewerUser;
  }

  public void setViewerUser(String viewerUser) {
    this.viewerUser = viewerUser;
  }

  public String getViewerPassword() {
    return viewerPassword;
  }

  public void setViewerPassword(String viewerPassword) {
    this.viewerPassword = viewerPassword;
  }
}
