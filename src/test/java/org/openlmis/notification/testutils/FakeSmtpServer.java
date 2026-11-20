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

package org.openlmis.notification.testutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Minimal SMTP server for tests. It optionally offers STARTTLS, upgraded with a self-signed
 * certificate for localhost, and AUTH PLAIN, which accepts any credentials. It accepts every
 * message and records what the client did.
 */
public class FakeSmtpServer implements AutoCloseable {

  private static final String KEYSTORE = "/smtp-test-keystore.p12";
  private static final char[] KEYSTORE_PASSWORD = "fakesmtp".toCharArray();
  private static final int SOCKET_TIMEOUT_MS = 10000;

  private final boolean offerStartTls;
  private final boolean offerAuth;
  private final SSLContext sslContext;
  private final ServerSocket serverSocket;

  private volatile FakeSmtpSession session = new FakeSmtpSession();

  /**
   * Starts the server on an ephemeral port of the loopback address.
   */
  public FakeSmtpServer(boolean offerStartTls, boolean offerAuth)
      throws IOException, GeneralSecurityException {
    this.offerStartTls = offerStartTls;
    this.offerAuth = offerAuth;
    this.sslContext = createSslContext();
    this.serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());

    Thread thread = new Thread(this::acceptLoop, "fake-smtp");
    thread.setDaemon(true);
    thread.start();
  }

  public String getHost() {
    return serverSocket.getInetAddress().getHostAddress();
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }

  public boolean isEhloSeen() {
    return session.isEhloSeen();
  }

  public boolean isAuthSeen() {
    return session.isAuthSeen();
  }

  public boolean isMailFromSeen() {
    return session.isMailFromSeen();
  }

  public boolean isMailFromOverTls() {
    return session.isMailFromOverTls();
  }

  @Override
  public void close() throws IOException {
    serverSocket.close();
  }

  private static SSLContext createSslContext() throws IOException, GeneralSecurityException {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream stream = FakeSmtpServer.class.getResourceAsStream(KEYSTORE)) {
      keyStore.load(stream, KEYSTORE_PASSWORD);
    }
    KeyManagerFactory keyManagers = KeyManagerFactory
        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagers.init(keyStore, KEYSTORE_PASSWORD);

    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagers.getKeyManagers(), null, null);
    return context;
  }

  private void acceptLoop() {
    while (!serverSocket.isClosed()) {
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        session = new FakeSmtpSession(socket, sslContext, offerStartTls, offerAuth);
        session.run();
      } catch (IOException ex) {
        // A client that drops the connection, for example on a failed handshake, is expected.
        if (serverSocket.isClosed()) {
          return;
        }
      }
    }
  }
}
