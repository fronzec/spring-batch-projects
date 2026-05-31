/* 2024-2025 */
package com.fronzec.frbatchservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised security credentials from {@code application.properties}.
 *
 * <p>Note: Passwords are stored in plain text ({@code {noop}}) for development.
 * Migrate to {@code {bcrypt}} hashes before production deployment.
 */
@ConfigurationProperties("app.security")
public class SecurityProperties {

  /** Username for the admin role (PLUGIN_ADMIN). */
  private String adminUser = "admin";

  /** Password for the admin user — plain text in dev, bcrypt hash in production. */
  private String adminPassword = "{noop}admin123";

  /** Username for the viewer role (PLUGIN_VIEWER). */
  private String viewerUser = "viewer";

  /** Password for the viewer user — plain text in dev, bcrypt hash in production. */
  private String viewerPassword = "{noop}viewer123";

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
