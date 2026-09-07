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

package org.openlmis.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.junit.Before;
import org.junit.Test;

public class TrustStoreLoaderTest {

  private static final String TYPE = "JKS";
  private static final char[] PASSWORD = "changeit".toCharArray();
  /**
   * Throwaway self-signed certificate, generated for this test and inlined so no resource root is
   * needed. It has to be a parseable X.509 certificate rather than arbitrary bytes, because the
   * loader is expected to accept it; no private key for it exists anywhere.
   */
  private static final String TEST_CERTIFICATE = ""
      + "-----BEGIN CERTIFICATE-----\n"
      + "MIIDcTCCAlmgAwIBAgIUEWhBKISuMTdu6UHEpo00kkkXXQ0wDQYJKoZIhvcNAQEL\n"
      + "BQAwSDEmMCQGA1UEAwwdT3BlbkxNSVMgTm90aWZpY2F0aW9uIFRlc3QgQ0ExETAP\n"
      + "BgNVBAoMCE9wZW5MTUlTMQswCQYDVQQGEwJVUzAeFw0yNjA5MDQwODI3MDNaFw00\n"
      + "NjA4MzAwODI3MDNaMEgxJjAkBgNVBAMMHU9wZW5MTUlTIE5vdGlmaWNhdGlvbiBU\n"
      + "ZXN0IENBMREwDwYDVQQKDAhPcGVuTE1JUzELMAkGA1UEBhMCVVMwggEiMA0GCSqG\n"
      + "SIb3DQEBAQUAA4IBDwAwggEKAoIBAQDgOOsopC0cWsP58Zy43GXI5OMZyUJHKO6z\n"
      + "T7oFjo+FtKN779TdPbNXegWOT7/QrvsjqZTfCLyCGD7j7HzA0XVEWwcWSC57VJcb\n"
      + "MkkEKgDPUxRqDVqLN6P6hDSXzTcrZUkF2baeMEha6w9TnvBPjV6QTP49PHrQ4LoZ\n"
      + "AEFQ3oJO2XFLsGgm6OMODD82VL7nar+W8kCSpT0zokm+VU4IJugmWmwq1eIR93jW\n"
      + "nx5W8HWG9a1WU863vFXney/DRXlsSe95rikR+Xu3a2HXEf5vSARzjU/eiYsSE7wO\n"
      + "kQcBS4q1sg7Gbq/mxx7B0U2i9HPJ638yudP9nc4kyLxsaP9YXARxAgMBAAGjUzBR\n"
      + "MB0GA1UdDgQWBBRmhgUQhR9HiJGuCu8uaW+vJmVQDTAfBgNVHSMEGDAWgBRmhgUQ\n"
      + "hR9HiJGuCu8uaW+vJmVQDTAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUA\n"
      + "A4IBAQC4OB3tyjWdXwKaPuUtYyA3KgMTjusW855tgCYsDbp+7DN8AFvzzFXrUNOQ\n"
      + "A+E3/GBkQaVvUzEGNXUuKf3CwV8OhxBQRtZv2nA8IFWNewxqlYgpnAgw4SxgpQh1\n"
      + "77wSqjw4H8ByxpdmBV8VzeWxoS20UBuEVZcPeVhpVleBWuLkEIZJcX5iVEWOkOGA\n"
      + "3ER0C7UbP3uKrTZDq4ftWeOjFBWT+boraUuy2Wu46HuXkCW05VTGjhVBnN9qUhMV\n"
      + "ppDLOgIOzC9WFB951B/8SJIbTz4YQ0F30W1XNn/EdoKaAXxrw13mhx1Wwi6CUpuh\n"
      + "loC/HutMrnZkpVWqyaq8ve9cBHxw\n"
      + "-----END CERTIFICATE-----\n";

  private TrustStoreLoader loader;

  @Before
  public void setUp() {
    loader = new TrustStoreLoader(TYPE, PASSWORD);
  }

  @Test
  public void shouldLoadTrustAnchorsFromTheStore() throws Exception {
    assertThat(loader.load(trustStore(true)).getAcceptedIssuers())
        .hasSize(1)
        .allSatisfy(issuer -> assertThat(issuer.getSubjectDN().getName())
            .contains("OpenLMIS Notification Test CA"));
  }

  @Test
  public void shouldRejectAStoreWithoutAnyTrustAnchor() throws Exception {
    // An empty store would reject every certificate - worse than keeping the previous one.
    assertThatThrownBy(() -> loader.load(trustStore(false)))
        .hasMessageContaining("does not contain any trust anchor");
  }

  @Test
  public void shouldRejectContentThatIsNotAStore() {
    byte[] content = "this is not a key store".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> loader.load(content)).isInstanceOf(Exception.class);
  }

  @Test
  public void shouldRejectATruncatedStore() throws Exception {
    byte[] store = trustStore(true);
    byte[] truncated = new byte[store.length / 2];
    System.arraycopy(store, 0, truncated, 0, truncated.length);

    // Half a file is what a reader can see while the store is being replaced in place.
    assertThatThrownBy(() -> loader.load(truncated)).isInstanceOf(Exception.class);
  }

  @Test
  public void shouldRejectAStoreOpenedWithTheWrongPassword() throws Exception {
    byte[] store = trustStore(true);

    assertThatThrownBy(() -> new TrustStoreLoader(TYPE, "wrong".toCharArray()).load(store))
        .isInstanceOf(Exception.class);
  }

  private byte[] trustStore(boolean withCertificate) throws Exception {
    KeyStore keyStore = KeyStore.getInstance(TYPE);
    keyStore.load(null, PASSWORD);

    if (withCertificate) {
      keyStore.setCertificateEntry("test-ca", readCertificate());
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    keyStore.store(output, PASSWORD);

    return output.toByteArray();
  }

  private X509Certificate readCertificate() throws Exception {
    byte[] encoded = TEST_CERTIFICATE.getBytes(StandardCharsets.US_ASCII);

    return (X509Certificate) CertificateFactory
        .getInstance("X.509")
        .generateCertificate(new ByteArrayInputStream(encoded));
  }

}
