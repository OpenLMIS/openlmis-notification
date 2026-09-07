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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Builds a trust manager from trust store contents. Takes the bytes rather than a path so the
 * caller can hash exactly what was loaded. A store that cannot be parsed, or that holds no trust
 * anchors, is rejected, so a half written file cannot leave the service trusting nothing.
 */
class TrustStoreLoader {

  private final String type;
  private final char[] password;

  TrustStoreLoader(String type, char[] password) {
    this.type = type;
    this.password = password;
  }

  /**
   * Turns the given trust store contents into a trust manager.
   *
   * @return a trust manager holding at least one trust anchor
   */
  X509ExtendedTrustManager load(byte[] content) throws GeneralSecurityException, IOException {
    KeyStore keyStore = KeyStore.getInstance(type);

    try (InputStream input = new ByteArrayInputStream(content)) {
      keyStore.load(input, password);
    }

    TrustManagerFactory factory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(keyStore);

    for (TrustManager manager : factory.getTrustManagers()) {
      if (manager instanceof X509ExtendedTrustManager) {
        X509ExtendedTrustManager x509Manager = (X509ExtendedTrustManager) manager;

        if (0 == x509Manager.getAcceptedIssuers().length) {
          throw new GeneralSecurityException(
              "The trust store does not contain any trust anchor");
        }

        return x509Manager;
      }
    }

    throw new GeneralSecurityException(
        "The trust store did not yield an X509ExtendedTrustManager");
  }

}
