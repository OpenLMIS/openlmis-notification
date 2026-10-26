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

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Trust manager whose anchors can be swapped while the service runs, so the SSLContext built over
 * it never has to be rebuilt. Extends {@link X509ExtendedTrustManager} rather than implementing
 * X509TrustManager on purpose: JSSE wraps the plain interface in an adapter that drops the socket
 * and engine aware callbacks, and with them hostname verification.
 */
public class ReloadableTrustManager extends X509ExtendedTrustManager {

  private final AtomicReference<X509ExtendedTrustManager> delegate = new AtomicReference<>();

  /**
   * Replaces the anchors used by every subsequent handshake. Handshakes already in flight keep
   * the delegate they started with.
   */
  void set(X509ExtendedTrustManager newDelegate) {
    delegate.set(newDelegate);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    delegate().checkClientTrusted(chain, authType);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    delegate().checkClientTrusted(chain, authType, socket);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
      throws CertificateException {
    delegate().checkClientTrusted(chain, authType, engine);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    delegate().checkServerTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    delegate().checkServerTrusted(chain, authType, socket);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
      throws CertificateException {
    delegate().checkServerTrusted(chain, authType, engine);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    X509ExtendedTrustManager current = delegate.get();

    return null == current ? new X509Certificate[0] : current.getAcceptedIssuers();
  }

  private X509ExtendedTrustManager delegate() throws CertificateException {
    X509ExtendedTrustManager current = delegate.get();

    if (null == current) {
      throw new CertificateException("No trust store has been loaded");
    }

    return current;
  }

}
