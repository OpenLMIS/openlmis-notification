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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Reads the certificates of an SMTP trust store file and builds trust managers over them. Takes
 * the bytes rather than a path so the caller can hash exactly what was loaded.
 */
class TrustStoreLoader {

  // CertificateFactory stops at the first PEM block that is not a certificate, such as the key
  // of a full chain file, so the certificate blocks are picked out first.
  private static final Pattern PEM_CERTIFICATE = Pattern.compile(
      "-----BEGIN CERTIFICATE-----.+?-----END CERTIFICATE-----", Pattern.DOTALL);

  private final TrustStoreType type;
  private final char[] password;

  TrustStoreLoader(TrustStoreType type, char[] password) {
    this.type = type;
    this.password = password;
  }

  /**
   * Reads every certificate in the given trust store contents.
   *
   * @throws GeneralSecurityException if the contents hold no certificate
   */
  List<X509Certificate> readCertificates(byte[] content)
      throws GeneralSecurityException, IOException {
    List<X509Certificate> certificates = type == TrustStoreType.PEM
        ? readPem(content)
        : readKeyStore(content);

    if (certificates.isEmpty()) {
      throw new GeneralSecurityException("The trust store does not contain any certificate");
    }
    return certificates;
  }

  /**
   * Returns the anchors the JVM trusts by default: those of javax.net.ssl.trustStore when it is
   * set, otherwise the JVM's cacerts.
   */
  static List<X509Certificate> jvmDefaultAnchors() throws GeneralSecurityException {
    return Arrays.asList(trustManager(null).getAcceptedIssuers());
  }

  static X509ExtendedTrustManager trustManagerFor(Collection<X509Certificate> anchors)
      throws GeneralSecurityException {
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    try {
      keyStore.load(null, null);
    } catch (IOException exp) {
      throw new KeyStoreException(exp);
    }

    int index = 0;
    for (X509Certificate anchor : anchors) {
      keyStore.setCertificateEntry("anchor-" + index, anchor);
      index++;
    }
    return trustManager(keyStore);
  }

  private List<X509Certificate> readPem(byte[] content) throws CertificateException {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    Matcher matcher = PEM_CERTIFICATE.matcher(new String(content, StandardCharsets.ISO_8859_1));

    List<X509Certificate> certificates = new ArrayList<>();
    while (matcher.find()) {
      byte[] block = matcher.group().getBytes(StandardCharsets.ISO_8859_1);
      certificates.add(
          (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(block)));
    }
    return certificates;
  }

  private List<X509Certificate> readKeyStore(byte[] content)
      throws GeneralSecurityException, IOException {
    KeyStore keyStore = KeyStore.getInstance(type.name());
    try (InputStream input = new ByteArrayInputStream(content)) {
      keyStore.load(input, password);
    }

    List<X509Certificate> certificates = new ArrayList<>();
    for (String alias : Collections.list(keyStore.aliases())) {
      Certificate certificate = keyStore.getCertificate(alias);
      if (certificate instanceof X509Certificate) {
        certificates.add((X509Certificate) certificate);
      }
    }
    return certificates;
  }

  private static X509ExtendedTrustManager trustManager(KeyStore keyStore)
      throws GeneralSecurityException {
    TrustManagerFactory factory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(keyStore);

    for (TrustManager manager : factory.getTrustManagers()) {
      if (manager instanceof X509ExtendedTrustManager) {
        return (X509ExtendedTrustManager) manager;
      }
    }
    throw new GeneralSecurityException("No X509ExtendedTrustManager is available");
  }
}
