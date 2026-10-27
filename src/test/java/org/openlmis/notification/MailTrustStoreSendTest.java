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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openlmis.notification.mail.MailTrustStore;
import org.openlmis.notification.testutils.FakeSmtpServer;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@SuppressWarnings("PMD.TooManyMethods")
public class MailTrustStoreSendTest {

  private static final String SSL = "ssl";
  private static final String STARTTLS = "starttls";
  private static final String TEST_CERT = "/smtp-test-cert.pem";
  private static final String OTHER_CERT = "/smtp-other-cert.pem";
  private static final String PEM = "PEM";
  private static final String PASSWORD = "secret";
  private static final String PKIX_FAILURE = "PKIX path building failed";

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private FakeSmtpServer server;

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.close();
    }
  }

  @Test
  public void shouldSendOverImplicitTlsWithPem() throws Exception {
    // given
    server = new FakeSmtpServer(true, false, false);

    // when
    send(SSL, trustStore(resource(TEST_CERT), PEM, ""));

    // then
    assertThat(server.isMailFromOverTls()).isTrue();
  }

  @Test
  public void shouldSendOverStartTlsWithPem() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);

    // when
    send(STARTTLS, trustStore(resource(TEST_CERT), PEM, ""));

    // then
    assertThat(server.isMailFromOverTls()).isTrue();
  }

  @Test
  public void shouldSendOverStartTlsWithPkcs12() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);

    // when
    send(STARTTLS, trustStore(keyStore("PKCS12"), "PKCS12", PASSWORD));

    // then
    assertThat(server.isMailFromOverTls()).isTrue();
  }

  @Test
  public void shouldSendOverImplicitTlsWithJks() throws Exception {
    // given
    server = new FakeSmtpServer(true, false, false);

    // when
    send(SSL, trustStore(keyStore("JKS"), "JKS", PASSWORD));

    // then
    assertThat(server.isMailFromOverTls()).isTrue();
  }

  @Test
  public void shouldRejectUnknownCertificateOverImplicitTls() throws Exception {
    // given
    server = new FakeSmtpServer(true, false, false);
    final MailTrustStore trustStore = trustStore(resource(OTHER_CERT), PEM, "");

    // when
    assertThatThrownBy(() -> send(SSL, trustStore))
        .isInstanceOf(MailSendException.class)
        .hasStackTraceContaining(PKIX_FAILURE);

    // then
    assertThat(server.isMailFromSeen()).isFalse();
  }

  @Test
  public void shouldRejectUnknownCertificateOverStartTls() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);
    final MailTrustStore trustStore = trustStore(resource(OTHER_CERT), PEM, "");

    // when
    assertThatThrownBy(() -> send(STARTTLS, trustStore))
        .isInstanceOf(MailSendException.class)
        .hasStackTraceContaining(PKIX_FAILURE);

    // then
    assertThat(server.isEhloSeen()).isTrue();
    assertThat(server.isMailFromSeen()).isFalse();
  }

  @Test
  public void shouldRejectSelfSignedWithoutTrustStore() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);
    final MailTrustStore trustStore = new MailTrustStore("", PEM, "", "0");
    trustStore.install();

    // when
    assertThatThrownBy(() -> send(STARTTLS, trustStore))
        .isInstanceOf(MailSendException.class)
        .hasStackTraceContaining(PKIX_FAILURE);

    // then
    assertThat(server.isMailFromSeen()).isFalse();
  }

  @Test
  public void shouldPickUpReplacedFile() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);
    final Path file = file(resource(OTHER_CERT));
    final MailTrustStore trustStore = trustStore(file, PEM, "");
    final JavaMailSenderImpl sender = sender(STARTTLS, trustStore);
    assertThatThrownBy(() -> send(sender))
        .hasStackTraceContaining(PKIX_FAILURE);

    // when
    Files.write(file, resource(TEST_CERT));
    trustStore.reloadIfChanged();
    send(sender);

    // then
    assertThat(server.isMailFromOverTls()).isTrue();
  }

  @Test
  public void shouldKeepCertificatesWhenReplacementBroken() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);
    final Path file = file(resource(TEST_CERT));
    final MailTrustStore trustStore = trustStore(file, PEM, "");
    final JavaMailSenderImpl sender = sender(STARTTLS, trustStore);

    // when
    Files.write(file, "not a certificate".getBytes(StandardCharsets.US_ASCII));
    trustStore.reloadIfChanged();
    // The first send comes after the reload, so no earlier TLS session can be resumed past
    // certificate validation.
    send(sender);

    // then
    assertThat(server.isMailFromOverTls()).isTrue();
  }

  @Test
  public void shouldStopTrustingRemovedCertificateAfterReload() throws Exception {
    // given
    server = new FakeSmtpServer(true, false);
    final Path file = file(resource(TEST_CERT));
    final MailTrustStore trustStore = trustStore(file, PEM, "");
    final JavaMailSenderImpl sender = sender(STARTTLS, trustStore);
    send(sender);

    // when
    Files.write(file, resource(OTHER_CERT));
    trustStore.reloadIfChanged();

    // then
    assertThatThrownBy(() -> send(sender))
        .isInstanceOf(MailSendException.class)
        .hasStackTraceContaining(PKIX_FAILURE);
  }

  private void send(String security, MailTrustStore trustStore) {
    send(sender(security, trustStore));
  }

  private void send(JavaMailSenderImpl sender) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom("noreply@test.org");
    message.setTo("user@test.org");
    message.setSubject("subject");
    message.setText("body");
    sender.send(message);
  }

  private JavaMailSenderImpl sender(String security, MailTrustStore trustStore) {
    MailProperties properties = new MailProperties();
    properties.setHost(server.getHost());
    properties.setPort(server.getPort());
    properties.setUsername("user");
    properties.setPassword(PASSWORD);
    properties.setProtocol("smtp");
    properties.getProperties().put("mail.smtp.auth", "true");
    properties.getProperties().put("mail.smtp.quitwait", "false");
    properties.getProperties().put("mail.smtp.connectiontimeout", "5000");
    properties.getProperties().put("mail.smtp.timeout", "5000");
    return new MailConfiguration().mailSender(properties, security, trustStore);
  }

  private MailTrustStore trustStore(byte[] content, String type, String password)
      throws IOException {
    return trustStore(file(content), type, password);
  }

  private MailTrustStore trustStore(Path path, String type, String password) {
    MailTrustStore trustStore = new MailTrustStore(path.toString(), type, password, "0");
    trustStore.install();
    return trustStore;
  }

  private Path file(byte[] content) throws IOException {
    Path path = folder.newFile().toPath();
    Files.write(path, content);
    return path;
  }

  private byte[] resource(String name) throws IOException {
    try (InputStream stream = getClass().getResourceAsStream(name)) {
      return IOUtils.toByteArray(stream);
    }
  }

  private byte[] keyStore(String type) throws IOException, GeneralSecurityException {
    KeyStore keyStore = KeyStore.getInstance(type);
    keyStore.load(null, null);
    try (InputStream stream = getClass().getResourceAsStream(TEST_CERT)) {
      keyStore.setCertificateEntry("smtp",
          CertificateFactory.getInstance("X.509").generateCertificate(stream));
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    keyStore.store(output, PASSWORD.toCharArray());
    return output.toByteArray();
  }
}
