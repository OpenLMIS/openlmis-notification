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

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Certificates trusted for SMTP connections only, on top of the JVM default ones. The rest of the
 * service keeps the JVM default trust.
 */
@Component
public class MailTrustStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(MailTrustStore.class);

  private final String path;
  private final TrustStoreLoader loader;
  private final ReloadableTrustManager trustManager = new ReloadableTrustManager();

  private SSLSocketFactory socketFactory;

  /**
   * Creates the trust store from the MAIL_TRUSTSTORE_* settings.
   *
   * @throws IllegalArgumentException if the type is none of PEM, JKS or PKCS12
   */
  public MailTrustStore(@Value("${email.smtp.truststore.path}") String path,
      @Value("${email.smtp.truststore.type}") String type,
      @Value("${email.smtp.truststore.password}") String password,
      @Value("${email.smtp.truststore.reloadIntervalSeconds}") long reloadIntervalSeconds) {
    this.path = path;
    this.loader = new TrustStoreLoader(TrustStoreType.fromValue(type),
        isBlank(password) ? null : password.toCharArray());
  }

  /**
   * Builds the SMTP socket factory, unless no trust store is configured. A file that cannot be
   * loaded is logged and leaves only the JVM default certificates trusted.
   */
  @PostConstruct
  public void install() {
    if (isBlank(path)) {
      LOGGER.info("No SMTP trust store configured, SMTP uses the JVM default trust store");
      return;
    }

    try {
      List<X509Certificate> defaults = TrustStoreLoader.jvmDefaultAnchors();
      trustManager.set(TrustStoreLoader.trustManagerFor(defaults));
      load(Paths.get(path), defaults);

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {trustManager}, null);
      socketFactory = context.getSocketFactory();
    } catch (GeneralSecurityException exp) {
      throw new IllegalStateException("Could not set up the SMTP trust store", exp);
    }
  }

  public Optional<SSLSocketFactory> getSocketFactory() {
    return Optional.ofNullable(socketFactory);
  }

  X509Certificate[] getAcceptedIssuers() {
    return trustManager.getAcceptedIssuers();
  }

  private void load(Path file, List<X509Certificate> defaults) throws GeneralSecurityException {
    List<X509Certificate> certificates;
    try {
      certificates = loader.readCertificates(Files.readAllBytes(file));
    } catch (IOException | GeneralSecurityException exp) {
      LOGGER.error("Could not load the SMTP trust store {}, SMTP trusts only the JVM default"
          + " certificates: {}", file, exp.toString());
      return;
    }

    List<X509Certificate> anchors = new ArrayList<>(defaults);
    anchors.addAll(certificates);
    trustManager.set(TrustStoreLoader.trustManagerFor(anchors));
    LOGGER.info("SMTP trusts {} certificate(s) from {} in addition to the JVM default ones",
        certificates.size(), file);
  }
}
