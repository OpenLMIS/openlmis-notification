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

package org.openlmis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@ActiveProfiles({"test", "test-run"})
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
    "notificationToSend.autoStartup=false",
    "spring.mail.host=localhost",
    "MAIL_SMTP_CONNECTION_TIMEOUT_MS=1000",
    "MAIL_SMTP_TIMEOUT_MS=1000",
    "MAIL_SMTP_WRITE_TIMEOUT_MS=1000"})
@ContextConfiguration(initializers = SmtpTimeoutIntegrationTest.SilentSmtpServer.class)
public class SmtpTimeoutIntegrationTest {

  @Autowired
  private EmailSender emailSender;

  @AfterClass
  public static void tearDown() throws IOException {
    SilentSmtpServer.close();
  }

  @Test(timeout = 20000)
  public void shouldFailWhenSmtpServerAcceptsButNeverAnswers() {
    long start = System.nanoTime();

    assertThatThrownBy(() -> emailSender.sendMail("user@example.org", "subject", "body"))
        .isInstanceOf(ServerException.class)
        .hasRootCauseInstanceOf(SocketTimeoutException.class);

    assertThat(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start)).isLessThan(10);
  }

  /**
   * Accepts every connection and keeps it open without ever writing a byte.
   */
  static class SilentSmtpServer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final List<Socket> CLIENTS = new CopyOnWriteArrayList<>();
    private static ServerSocket server;

    @Override
    public void initialize(ConfigurableApplicationContext context) {
      try {
        server = new ServerSocket(0);
      } catch (IOException ex) {
        throw new IllegalStateException(ex);
      }

      Thread acceptor = new Thread(() -> {
        while (!server.isClosed()) {
          try {
            CLIENTS.add(server.accept());
          } catch (IOException ex) {
            return;
          }
        }
      });
      acceptor.setDaemon(true);
      acceptor.start();

      TestPropertyValues
          .of("spring.mail.port=" + server.getLocalPort())
          .applyTo(context);
    }

    static void close() throws IOException {
      for (Socket client : CLIENTS) {
        client.close();
      }

      if (null != server) {
        server.close();
      }
    }
  }

}
