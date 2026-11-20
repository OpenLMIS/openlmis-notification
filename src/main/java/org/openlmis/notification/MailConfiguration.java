/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org.
 */

package org.openlmis.notification;

import java.util.Properties;
import javax.net.ssl.SSLSocketFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Replaces Spring Boot's auto-configured mail sender so that the SMTP connection security can be
 * chosen with one setting instead of raw JavaMail properties.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfiguration {

  private static final String SOCKET_FACTORY_CLASS = "mail.smtp.socketFactory.class";
  private static final String SOCKET_FACTORY_FALLBACK = "mail.smtp.socketFactory.fallback";
  private static final String STARTTLS_ENABLE = "mail.smtp.starttls.enable";
  private static final String STARTTLS_REQUIRED = "mail.smtp.starttls.required";

  /**
   * Builds the sender from the spring.mail properties, as Spring Boot does, plus the JavaMail
   * properties of the given connection security.
   *
   * @param security ssl (implicit TLS) or starttls (plain connection upgraded with STARTTLS)
   */
  @Bean
  public JavaMailSenderImpl mailSender(MailProperties properties,
      @Value("${email.smtp.security}") String security) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(properties.getHost());
    if (properties.getPort() != null) {
      sender.setPort(properties.getPort());
    }
    sender.setUsername(properties.getUsername());
    sender.setPassword(properties.getPassword());
    sender.setProtocol(properties.getProtocol());
    if (properties.getDefaultEncoding() != null) {
      sender.setDefaultEncoding(properties.getDefaultEncoding().name());
    }

    Properties javaMail = new Properties();
    javaMail.putAll(properties.getProperties());
    applySecurity(javaMail, SmtpSecurity.fromValue(security));
    sender.setJavaMailProperties(javaMail);
    return sender;
  }

  static void applySecurity(Properties javaMail, SmtpSecurity security) {
    if (security == SmtpSecurity.STARTTLS) {
      // Forced, so that no explicit property can let mail go out in plain text.
      javaMail.put(STARTTLS_ENABLE, "true");
      javaMail.put(STARTTLS_REQUIRED, "true");
    } else {
      // Defaults only: an explicit spring.mail.properties value, even a blank one, still wins.
      javaMail.putIfAbsent(SOCKET_FACTORY_CLASS, SSLSocketFactory.class.getName());
      javaMail.putIfAbsent(SOCKET_FACTORY_FALLBACK, "false");
    }
  }

  public enum SmtpSecurity {
    SSL, STARTTLS;

    /**
     * Parses the MAIL_SMTP_SECURITY value, ignoring case and surrounding spaces.
     *
     * @throws IllegalArgumentException if the value is neither ssl nor starttls
     */
    public static SmtpSecurity fromValue(String value) {
      String trimmed = StringUtils.trim(value);
      for (SmtpSecurity security : values()) {
        if (security.name().equalsIgnoreCase(trimmed)) {
          return security;
        }
      }
      throw new IllegalArgumentException(
          "MAIL_SMTP_SECURITY must be ssl or starttls, was: " + value);
    }
  }
}
