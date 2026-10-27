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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@SuppressWarnings("PMD.TooManyMethods")
public class TrustStoreLoaderTest {

  private static final String TEST_CERT = "/smtp-test-cert.pem";
  private static final String OTHER_CERT = "/smtp-other-cert.pem";
  private static final String TEST_SUBJECT = "CN=localhost";
  private static final String OTHER_SUBJECT = "CN=other";
  private static final char[] PASSWORD = "secret".toCharArray();
  private static final String AUTH_TYPE = "RSA";

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldReadPem() throws Exception {
    // when
    List<X509Certificate> certificates = pemLoader().readCertificates(resource(TEST_CERT));

    // then
    assertThat(subjects(certificates)).containsExactly(TEST_SUBJECT);
  }

  @Test
  public void shouldReadPemWithSeveralCertificates() throws Exception {
    // given
    byte[] content = concat(resource(TEST_CERT), resource(OTHER_CERT));

    // when
    List<X509Certificate> certificates = pemLoader().readCertificates(content);

    // then
    assertThat(subjects(certificates)).containsExactly(TEST_SUBJECT, OTHER_SUBJECT);
  }

  @Test
  public void shouldReadPemWithSurroundingText() throws Exception {
    // given
    byte[] content = concat(text("Mail server certificate\n"), resource(TEST_CERT),
        text("\nend of file\n"));

    // when
    List<X509Certificate> certificates = pemLoader().readCertificates(content);

    // then
    assertThat(subjects(certificates)).containsExactly(TEST_SUBJECT);
  }

  @Test
  public void shouldSkipNonCertificateBlocks() throws Exception {
    // given
    byte[] content = concat(resource(TEST_CERT), text(privateKeyBlock()));

    // when
    List<X509Certificate> certificates = pemLoader().readCertificates(content);

    // then
    assertThat(subjects(certificates)).containsExactly(TEST_SUBJECT);
  }

  @Test
  public void shouldRejectEmptyPem() throws Exception {
    // then
    exception.expect(GeneralSecurityException.class);

    // when
    pemLoader().readCertificates(new byte[0]);
  }

  @Test
  public void shouldRejectPemWithoutCertificates() throws Exception {
    // then
    exception.expect(GeneralSecurityException.class);

    // when
    pemLoader().readCertificates(text(privateKeyBlock()));
  }

  @Test
  public void shouldReadJks() throws Exception {
    // when
    List<X509Certificate> certificates = new TrustStoreLoader(TrustStoreType.JKS, PASSWORD)
        .readCertificates(keyStore("JKS", PASSWORD));

    // then
    assertThat(subjects(certificates)).containsExactly(TEST_SUBJECT);
  }

  @Test
  public void shouldReadPkcs12() throws Exception {
    // when
    List<X509Certificate> certificates = new TrustStoreLoader(TrustStoreType.PKCS12, PASSWORD)
        .readCertificates(keyStore("PKCS12", PASSWORD));

    // then
    assertThat(subjects(certificates)).containsExactly(TEST_SUBJECT);
  }

  @Test
  public void shouldReadJksWithoutPassword() throws Exception {
    // when
    List<X509Certificate> certificates = new TrustStoreLoader(TrustStoreType.JKS, null)
        .readCertificates(keyStore("JKS", PASSWORD));

    // then
    assertThat(subjects(certificates)).containsExactly(TEST_SUBJECT);
  }

  @Test
  public void shouldRejectKeyStoreWithWrongPassword() throws Exception {
    // then
    exception.expect(IOException.class);

    // when
    new TrustStoreLoader(TrustStoreType.JKS, "wrong".toCharArray())
        .readCertificates(keyStore("JKS", PASSWORD));
  }

  @Test
  public void shouldTrustDefaultsAndConfiguredCertificates() throws Exception {
    // given
    List<X509Certificate> defaults = TrustStoreLoader.jvmDefaultAnchors();
    List<X509Certificate> anchors = new ArrayList<>(defaults);
    anchors.addAll(pemLoader().readCertificates(resource(TEST_CERT)));

    // when
    X509Certificate[] accepted = TrustStoreLoader.trustManagerFor(anchors).getAcceptedIssuers();

    // then
    assertThat(defaults).isNotEmpty();
    assertThat(accepted)
        .hasSize(defaults.size() + 1)
        .contains(defaults.toArray(new X509Certificate[0]));
  }

  @Test
  public void shouldAcceptServerPresentingConfiguredCertificate() throws Exception {
    // given
    List<X509Certificate> anchors = new ArrayList<>(TrustStoreLoader.jvmDefaultAnchors());
    anchors.addAll(pemLoader().readCertificates(resource(TEST_CERT)));

    // when
    TrustStoreLoader.trustManagerFor(anchors)
        .checkServerTrusted(new X509Certificate[] {certificate(TEST_CERT)}, AUTH_TYPE);
  }

  @Test
  public void shouldRejectServerPresentingUnknownCertificate() throws Exception {
    // given
    List<X509Certificate> anchors = new ArrayList<>(TrustStoreLoader.jvmDefaultAnchors());
    anchors.addAll(pemLoader().readCertificates(resource(OTHER_CERT)));

    // then
    exception.expect(CertificateException.class);

    // when
    TrustStoreLoader.trustManagerFor(anchors)
        .checkServerTrusted(new X509Certificate[] {certificate(TEST_CERT)}, AUTH_TYPE);
  }

  @Test
  public void shouldRejectUnknownType() {
    // then
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage("MAIL_TRUSTSTORE_TYPE must be PEM, JKS or PKCS12, was: pem12");

    // when
    TrustStoreType.fromValue("pem12");
  }

  @Test
  public void shouldParseBlankTypeAsPem() {
    assertThat(TrustStoreType.fromValue(" ")).isEqualTo(TrustStoreType.PEM);
    assertThat(TrustStoreType.fromValue(null)).isEqualTo(TrustStoreType.PEM);
  }

  @Test
  public void shouldParseTypeIgnoringCaseAndSpaces() {
    assertThat(TrustStoreType.fromValue(" pkcs12 ")).isEqualTo(TrustStoreType.PKCS12);
    assertThat(TrustStoreType.fromValue("Pem")).isEqualTo(TrustStoreType.PEM);
  }

  private TrustStoreLoader pemLoader() {
    return new TrustStoreLoader(TrustStoreType.PEM, null);
  }

  private byte[] resource(String name) throws IOException {
    try (InputStream stream = getClass().getResourceAsStream(name)) {
      return IOUtils.toByteArray(stream);
    }
  }

  private X509Certificate certificate(String name) throws IOException, CertificateException {
    try (InputStream stream = getClass().getResourceAsStream(name)) {
      return (X509Certificate) CertificateFactory.getInstance("X.509")
          .generateCertificate(stream);
    }
  }

  private byte[] keyStore(String type, char[] password)
      throws IOException, GeneralSecurityException {
    KeyStore keyStore = KeyStore.getInstance(type);
    keyStore.load(null, null);
    keyStore.setCertificateEntry("smtp", certificate(TEST_CERT));

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    keyStore.store(output, password);
    return output.toByteArray();
  }

  private String privateKeyBlock() {
    return "-----BEGIN PRIVATE KEY-----\nMIIBVQIBADANBgkqhkiG9w0BAQEFAASCAT8wggE7\n"
        + "-----END PRIVATE KEY-----\n";
  }

  private byte[] text(String value) {
    return value.getBytes(StandardCharsets.US_ASCII);
  }

  private byte[] concat(byte[]... parts) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      output.write(part);
    }
    return output.toByteArray();
  }

  private List<String> subjects(List<X509Certificate> certificates) {
    return certificates.stream()
        .map(certificate -> certificate.getSubjectX500Principal().getName())
        .collect(Collectors.toList());
  }
}
