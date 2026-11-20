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

package org.openlmis.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

@SuppressWarnings("PMD.TooManyMethods")
public class MailTrustStoreTest {

  private static final String TEST_SUBJECT = "CN=localhost";
  private static final String OTHER_SUBJECT = "CN=other";
  private static final String WATCHER = "mail-truststore-watch";
  private static final String PEM = "PEM";

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldBeDisabledWithoutPath() {
    // given
    MailTrustStore trustStore = new MailTrustStore("", PEM, "", "0");

    // when
    trustStore.install();

    // then
    assertThat(trustStore.getSocketFactory()).isEmpty();
  }

  @Test
  public void shouldTrustFileAndDefaults() throws Exception {
    // given
    List<X509Certificate> defaults = TrustStoreLoader.jvmDefaultAnchors();
    MailTrustStore trustStore = new MailTrustStore(file(testCertificate()), PEM, "", "0");

    // when
    trustStore.install();

    // then
    assertThat(trustStore.getSocketFactory()).isPresent();
    assertThat(trustStore.getAcceptedIssuers())
        .hasSize(defaults.size() + 1)
        .contains(defaults.toArray(new X509Certificate[0]));
    assertThat(subjects(trustStore.getAcceptedIssuers())).contains(TEST_SUBJECT);
  }

  @Test
  public void shouldFallBackToDefaultsWhenFileMissing() throws Exception {
    // given
    String missing = folder.getRoot().toPath().resolve("missing.pem").toString();
    MailTrustStore trustStore = new MailTrustStore(missing, PEM, "", "0");

    // when
    trustStore.install();

    // then
    assertThat(trustStore.getSocketFactory()).isPresent();
    assertThat(trustStore.getAcceptedIssuers())
        .containsExactlyInAnyOrder(defaultAnchors());
  }

  @Test
  public void shouldFallBackToDefaultsWhenFileUnparsable() throws Exception {
    // given
    String garbage = file("not a certificate".getBytes(StandardCharsets.US_ASCII));
    MailTrustStore trustStore = new MailTrustStore(garbage, PEM, "", "0");

    // when
    trustStore.install();

    // then
    assertThat(trustStore.getSocketFactory()).isPresent();
    assertThat(trustStore.getAcceptedIssuers())
        .containsExactlyInAnyOrder(defaultAnchors());
  }

  @Test
  public void shouldNotChangeJvmDefaultTls() throws Exception {
    // given
    final SSLContext defaultContext = SSLContext.getDefault();
    final SocketFactory defaultHttpsFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
    MailTrustStore trustStore = new MailTrustStore(file(testCertificate()), PEM, "", "0");

    // when
    trustStore.install();

    // then
    assertThat(SSLContext.getDefault()).isSameAs(defaultContext);
    assertThat(HttpsURLConnection.getDefaultSSLSocketFactory()).isSameAs(defaultHttpsFactory);
    assertThat(subjects(defaultAnchors())).doesNotContain(TEST_SUBJECT);
  }

  @Test
  public void shouldTreatBlankIntervalAsZero() throws Exception {
    // given
    MailTrustStore trustStore = new MailTrustStore(file(testCertificate()), PEM, "", " ");

    // when
    trustStore.install();

    // then
    assertThat(trustStore.getSocketFactory()).isPresent();
    assertThat(watcherThread()).isEmpty();
  }

  @Test
  public void shouldTreatBlankTypeAsPem() throws Exception {
    // given
    MailTrustStore trustStore = new MailTrustStore(file(testCertificate()), "", "", "0");

    // when
    trustStore.install();

    // then
    assertThat(subjects(trustStore.getAcceptedIssuers())).contains(TEST_SUBJECT);
  }

  @Test
  public void shouldTrimPath() throws Exception {
    // given
    MailTrustStore trustStore = new MailTrustStore(file(testCertificate()) + " ", PEM, "", "0");

    // when
    trustStore.install();

    // then
    assertThat(subjects(trustStore.getAcceptedIssuers())).contains(TEST_SUBJECT);
  }

  @Test
  public void shouldFailOnUnknownType() {
    // then
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("MAIL_TRUSTSTORE_TYPE must be PEM, JKS or PKCS12");

    // when
    new MailTrustStore("", "pem12", "", "0");
  }

  @Test
  public void shouldLoadFileFixedAfterBrokenReplacement() throws Exception {
    // given
    String path = file(resource("/smtp-other-cert.pem"));
    MailTrustStore trustStore = new MailTrustStore(path, PEM, "", "0");
    trustStore.install();

    // when
    write(path, "-----BEGIN CERTIFICATE-----\nbroken".getBytes(StandardCharsets.US_ASCII));
    trustStore.reloadIfChanged();
    final List<String> afterBroken = subjects(trustStore.getAcceptedIssuers());
    write(path, testCertificate());
    trustStore.reloadIfChanged();

    // then
    assertThat(afterBroken).contains(OTHER_SUBJECT);
    assertThat(subjects(trustStore.getAcceptedIssuers()))
        .contains(TEST_SUBJECT)
        .doesNotContain(OTHER_SUBJECT);
  }

  @Test
  public void shouldPickUpFileCreatedAfterStart() throws Exception {
    // given
    String path = folder.getRoot().toPath().resolve("later.pem").toString();
    MailTrustStore trustStore = new MailTrustStore(path, PEM, "", "0");
    trustStore.install();

    // when
    write(path, testCertificate());
    trustStore.reloadIfChanged();

    // then
    assertThat(trustStore.getAcceptedIssuers()).hasSize(defaultAnchors().length + 1);
    assertThat(subjects(trustStore.getAcceptedIssuers())).contains(TEST_SUBJECT);
  }

  @Test
  public void shouldNotStartWatcherWhenIntervalZero() throws Exception {
    // given
    MailTrustStore trustStore = new MailTrustStore(file(testCertificate()), PEM, "", "0");

    // when
    trustStore.install();

    // then
    assertThat(watcherThread()).isEmpty();
  }

  @Test
  public void shouldStartWatcherAndStopItOnShutdown() throws Exception {
    // given
    MailTrustStore trustStore = new MailTrustStore(file(testCertificate()), PEM, "", "60");
    trustStore.install();
    final Optional<Thread> watcher = watcherThread();
    assertThat(watcher).isPresent();

    // when
    trustStore.shutdown();
    watcher.get().join(5000);

    // then
    assertThat(watcher.get().isAlive()).isFalse();
  }

  private byte[] testCertificate() throws IOException {
    return resource("/smtp-test-cert.pem");
  }

  private byte[] resource(String name) throws IOException {
    try (InputStream stream = getClass().getResourceAsStream(name)) {
      return IOUtils.toByteArray(stream);
    }
  }

  private void write(String path, byte[] content) throws IOException {
    Files.write(Paths.get(path), content);
  }

  private Optional<Thread> watcherThread() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(thread -> WATCHER.equals(thread.getName()))
        .findFirst();
  }

  private String file(byte[] content) throws IOException {
    Path path = folder.newFile().toPath();
    Files.write(path, content);
    return path.toString();
  }

  private X509Certificate[] defaultAnchors() throws Exception {
    return TrustStoreLoader.jvmDefaultAnchors().toArray(new X509Certificate[0]);
  }

  private List<String> subjects(X509Certificate... certificates) {
    return Arrays.stream(certificates)
        .map(certificate -> certificate.getSubjectX500Principal().getName())
        .collect(Collectors.toList());
  }
}
