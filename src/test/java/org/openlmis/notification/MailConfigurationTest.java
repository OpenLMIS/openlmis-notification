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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openlmis.notification.MailConfiguration.SmtpSecurity;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;

public class MailConfigurationTest {

  private static final String SOCKET_FACTORY_CLASS = "mail.smtp.socketFactory.class";
  private static final String SOCKET_FACTORY_FALLBACK = "mail.smtp.socketFactory.fallback";
  private static final String STARTTLS_ENABLE = "mail.smtp.starttls.enable";
  private static final String STARTTLS_REQUIRED = "mail.smtp.starttls.required";
  private static final String TRUE = "true";
  private static final String FALSE = "false";

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private MailProperties mailProperties;

  @Before
  public void setUp() {
    mailProperties = new MailProperties();
    mailProperties.setHost("smtp.example.org");
    mailProperties.setPort(465);
    mailProperties.setUsername("user");
    mailProperties.setPassword("secret");
    mailProperties.setProtocol("smtp");
    mailProperties.getProperties().put("mail.smtp.auth", TRUE);
    mailProperties.getProperties().put(STARTTLS_ENABLE, TRUE);
    mailProperties.getProperties().put("mail.smtp.quitwait", FALSE);
  }

  @Test
  public void shouldUseImplicitTlsByDefault() {
    // when
    final JavaMailSenderImpl sender = new MailConfiguration().mailSender(mailProperties, "ssl");

    // then
    Properties expected = new Properties();
    expected.put("mail.smtp.auth", TRUE);
    expected.put(STARTTLS_ENABLE, TRUE);
    expected.put("mail.smtp.quitwait", FALSE);
    expected.put(SOCKET_FACTORY_CLASS, "javax.net.ssl.SSLSocketFactory");
    expected.put(SOCKET_FACTORY_FALLBACK, FALSE);
    assertThat(sender.getJavaMailProperties()).isEqualTo(expected);
    assertThat(sender.getHost()).isEqualTo("smtp.example.org");
    assertThat(sender.getPort()).isEqualTo(465);
    assertThat(sender.getUsername()).isEqualTo("user");
    assertThat(sender.getPassword()).isEqualTo("secret");
    assertThat(sender.getProtocol()).isEqualTo("smtp");
  }

  @Test
  public void shouldRequireStartTlsInStartTlsMode() {
    // when
    Properties properties = new MailConfiguration()
        .mailSender(mailProperties, "starttls").getJavaMailProperties();

    // then
    assertThat(properties)
        .doesNotContainKeys(SOCKET_FACTORY_CLASS, SOCKET_FACTORY_FALLBACK)
        .containsEntry(STARTTLS_ENABLE, TRUE)
        .containsEntry(STARTTLS_REQUIRED, TRUE);
  }

  @Test
  public void shouldKeepExplicitSocketFactoryInSslMode() {
    // given
    mailProperties.getProperties().put(SOCKET_FACTORY_CLASS, "");

    // when
    Properties properties = new MailConfiguration()
        .mailSender(mailProperties, "ssl").getJavaMailProperties();

    // then
    assertThat(properties).containsEntry(SOCKET_FACTORY_CLASS, "");
  }

  @Test
  public void shouldForceStartTlsRequiredInStartTlsMode() {
    // given
    mailProperties.getProperties().put(STARTTLS_ENABLE, FALSE);
    mailProperties.getProperties().put(STARTTLS_REQUIRED, FALSE);

    // when
    Properties properties = new MailConfiguration()
        .mailSender(mailProperties, "starttls").getJavaMailProperties();

    // then
    assertThat(properties)
        .containsEntry(STARTTLS_ENABLE, TRUE)
        .containsEntry(STARTTLS_REQUIRED, TRUE);
  }

  @Test
  public void shouldRejectUnknownSecurity() {
    // then
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("MAIL_SMTP_SECURITY must be ssl or starttls, was: tls");

    // when
    new MailConfiguration().mailSender(mailProperties, "tls");
  }

  @Test
  public void shouldParseSecurityIgnoringCaseAndSpaces() {
    assertThat(SmtpSecurity.fromValue(" STARTTLS ")).isEqualTo(SmtpSecurity.STARTTLS);
    assertThat(SmtpSecurity.fromValue("Ssl")).isEqualTo(SmtpSecurity.SSL);
  }
}
