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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/**
 * One SMTP conversation of {@link FakeSmtpServer}, and what the client did in it.
 */
class FakeSmtpSession {

  private final SSLContext sslContext;
  private final boolean offerStartTls;
  private final boolean offerAuth;

  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;
  private boolean tls;

  private volatile boolean ehloSeen;
  private volatile boolean authSeen;
  private volatile boolean mailFromSeen;
  private volatile boolean mailFromOverTls;

  /**
   * An empty session, for before the first client connects.
   */
  FakeSmtpSession() {
    this.sslContext = null;
    this.offerStartTls = false;
    this.offerAuth = false;
  }

  FakeSmtpSession(Socket socket, SSLContext sslContext, boolean offerStartTls,
      boolean offerAuth) throws IOException {
    this.sslContext = sslContext;
    this.offerStartTls = offerStartTls;
    this.offerAuth = offerAuth;
    use(socket);
  }

  boolean isEhloSeen() {
    return ehloSeen;
  }

  boolean isAuthSeen() {
    return authSeen;
  }

  boolean isMailFromSeen() {
    return mailFromSeen;
  }

  boolean isMailFromOverTls() {
    return mailFromOverTls;
  }

  void run() throws IOException {
    reply("220 fake ESMTP");
    String line = in.readLine();
    while (line != null && handle(line.toUpperCase(Locale.ENGLISH))) {
      line = in.readLine();
    }
  }

  private boolean handle(String command) throws IOException {
    switch (command.split(" ")[0]) {
      case "EHLO":
        ehloSeen = true;
        reply(ehloReply());
        break;
      case "STARTTLS":
        startTls();
        break;
      case "AUTH":
        auth(command);
        break;
      case "MAIL":
        mailFromSeen = true;
        mailFromOverTls = tls;
        reply("250 ok");
        break;
      case "DATA":
        data();
        break;
      case "QUIT":
        reply("221 bye");
        return false;
      default:
        reply("250 ok");
    }
    return true;
  }

  private String ehloReply() {
    return "250-fake\r\n"
        + (offerStartTls && !tls ? "250-STARTTLS\r\n" : "")
        + (offerAuth ? "250-AUTH PLAIN\r\n" : "")
        + "250 8BITMIME";
  }

  private void startTls() throws IOException {
    if (!offerStartTls || tls) {
      reply("454 TLS not available");
      return;
    }
    reply("220 go ahead");
    SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory()
        .createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
    sslSocket.setUseClientMode(false);
    sslSocket.startHandshake();
    use(sslSocket);
    tls = true;
  }

  private void auth(String command) throws IOException {
    if (!offerAuth || !command.startsWith("AUTH PLAIN")) {
      reply("502 not supported");
      return;
    }
    if ("AUTH PLAIN".equals(command)) {
      reply("334 ");
      in.readLine();
    }
    authSeen = true;
    reply("235 accepted");
  }

  private void data() throws IOException {
    reply("354 go ahead");
    String line = in.readLine();
    while (line != null && !".".equals(line)) {
      line = in.readLine();
    }
    reply("250 queued");
  }

  private void use(Socket newSocket) throws IOException {
    socket = newSocket;
    in = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    out = new PrintWriter(socket.getOutputStream());
  }

  private void reply(String reply) {
    out.print(reply + "\r\n");
    out.flush();
  }
}
