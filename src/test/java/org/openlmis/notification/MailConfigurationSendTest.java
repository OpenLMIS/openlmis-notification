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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.net.ssl.SSLException;
import org.junit.After;
import org.junit.Test;
import org.openlmis.notification.testutils.FakeSmtpServer;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

public class MailConfigurationSendTest {

  private static final String SSL = "ssl";
  private static final String STARTTLS = "starttls";

  private FakeSmtpServer server;

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.close();
    }
  }

  @Test
  public void shouldSendOverStartTlsWithoutAuth() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);

    // when
    send(STARTTLS);

    // then
    assertThat(server.isMailFromOverTls()).isTrue();
    assertThat(server.isAuthSeen()).isFalse();
  }

  @Test
  public void shouldSendOverStartTlsWithAuth() throws Exception {
    // given
    server = new FakeSmtpServer(true, true);

    // when
    send(STARTTLS);

    // then
    assertThat(server.isMailFromOverTls()).isTrue();
    assertThat(server.isAuthSeen()).isTrue();
  }

  @Test
  public void shouldFailWhenServerDoesNotOfferStartTls() throws Exception {
    // given
    server = new FakeSmtpServer(false, false);

    // when
    assertThatThrownBy(() -> send(STARTTLS)).isInstanceOf(MailSendException.class);

    // then
    assertThat(server.isEhloSeen()).isTrue();
    assertThat(server.isMailFromSeen()).isFalse();
  }

  @Test
  public void shouldNotTalkPlainTextInSslMode() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);

    // when
    assertThatThrownBy(() -> send(SSL))
        .isInstanceOf(MailSendException.class)
        .hasRootCauseInstanceOf(SSLException.class);

    // then
    assertThat(server.isEhloSeen()).isFalse();
    assertThat(server.isMailFromSeen()).isFalse();
  }

  private void send(String security) {
    MailProperties properties = new MailProperties();
    properties.setHost(server.getHost());
    properties.setPort(server.getPort());
    properties.setUsername("user");
    properties.setPassword("secret");
    properties.setProtocol("smtp");
    properties.getProperties().put("mail.smtp.auth", "true");
    properties.getProperties().put("mail.smtp.quitwait", "false");
    properties.getProperties().put("mail.smtp.connectiontimeout", "5000");
    properties.getProperties().put("mail.smtp.timeout", "5000");
    // The server's certificate is self-signed.
    properties.getProperties().put("mail.smtp.ssl.trust", "*");
    final JavaMailSenderImpl sender = new MailConfiguration().mailSender(properties, security);

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom("noreply@test.org");
    message.setTo("user@test.org");
    message.setSubject("subject");
    message.setText("body");
    sender.send(message);
  }
}
