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
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Certificates trusted for SMTP connections only, on top of the JVM default ones. The rest of the
 * service keeps the JVM default trust. The file can be watched, so that a changed certificate
 * needs no restart.
 */
@Component
public class MailTrustStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(MailTrustStore.class);

  private static final String KEEP_DEFAULTS = "SMTP trusts only the JVM default certificates";
  private static final String KEEP_CURRENT = "keeping the certificates that are currently in use";

  private final String path;
  private final TrustStoreLoader loader;
  private final long reloadIntervalSeconds;
  private final ReloadableTrustManager trustManager = new ReloadableTrustManager();

  private Path file;
  private List<X509Certificate> defaults;
  private SSLContext context;
  private SSLSocketFactory socketFactory;
  private ScheduledExecutorService watcher;

  /**
   * Digest of the contents last attempted, successfully or not, so that a file that cannot be
   * loaded is not retried, and logged, on every check.
   */
  private volatile String attemptedDigest;

  private volatile boolean readFailureReported;

  /**
   * Creates the trust store from the MAIL_TRUSTSTORE_* settings. A blank type means PEM, and a
   * blank reload interval means no reloading.
   *
   * @throws IllegalArgumentException if the type is none of PEM, JKS or PKCS12, or the interval
   *     is not a number
   */
  public MailTrustStore(@Value("${email.smtp.truststore.path}") String path,
      @Value("${email.smtp.truststore.type}") String type,
      @Value("${email.smtp.truststore.password}") String password,
      @Value("${email.smtp.truststore.reloadIntervalSeconds}") String reloadIntervalSeconds) {
    this.path = path;
    this.loader = new TrustStoreLoader(TrustStoreType.fromValue(type),
        isBlank(password) ? null : password.toCharArray());
    this.reloadIntervalSeconds = isBlank(reloadIntervalSeconds)
        ? 0
        : Long.parseLong(reloadIntervalSeconds.trim());
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

    file = Paths.get(path);
    try {
      defaults = TrustStoreLoader.jvmDefaultAnchors();
      trustManager.set(TrustStoreLoader.trustManagerFor(defaults));

      byte[] content = read(KEEP_DEFAULTS);
      if (content != null) {
        swapIn(content, KEEP_DEFAULTS);
      }

      context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {trustManager}, null);
      socketFactory = context.getSocketFactory();
    } catch (GeneralSecurityException exp) {
      throw new IllegalStateException("Could not set up the SMTP trust store", exp);
    }

    startWatching();
  }

  /**
   * Loads the file again if its contents changed since the last attempt. A file that cannot be
   * read or loaded is logged once, and the certificates in use stay in use.
   */
  public void reloadIfChanged() {
    // Anything thrown here would silently cancel the scheduled check, so nothing gets out.
    try {
      byte[] content = read(KEEP_CURRENT);
      if (content != null && !Objects.equals(attemptedDigest, digest(content))) {
        LOGGER.info("SMTP trust store {} changed, reloading", file);
        swapIn(content, KEEP_CURRENT);
      }
    } catch (RuntimeException exp) {
      LOGGER.error("Unexpected failure while checking the SMTP trust store {}", file, exp);
    }
  }

  /**
   * Stops watching the file.
   */
  @PreDestroy
  public void shutdown() {
    if (watcher != null) {
      watcher.shutdownNow();
    }
  }

  public Optional<SSLSocketFactory> getSocketFactory() {
    return Optional.ofNullable(socketFactory);
  }

  X509Certificate[] getAcceptedIssuers() {
    return trustManager.getAcceptedIssuers();
  }

  private void startWatching() {
    if (reloadIntervalSeconds <= 0) {
      return;
    }

    watcher = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "mail-truststore-watch");
      thread.setDaemon(true);
      return thread;
    });
    watcher.scheduleWithFixedDelay(this::reloadIfChanged,
        reloadIntervalSeconds, reloadIntervalSeconds, TimeUnit.SECONDS);
    LOGGER.info("Checking the SMTP trust store {} for changes every {}s", file,
        reloadIntervalSeconds);
  }

  private byte[] read(String outcome) {
    try {
      byte[] content = Files.readAllBytes(file);
      readFailureReported = false;
      return content;
    } catch (IOException exp) {
      if (!readFailureReported) {
        readFailureReported = true;
        LOGGER.error("Could not read the SMTP trust store {}, {}: {}", file, outcome,
            exp.toString());
      }
      return null;
    }
  }

  private void swapIn(byte[] content, String outcome) {
    attemptedDigest = digest(content);
    try {
      List<X509Certificate> certificates = loader.readCertificates(content);
      List<X509Certificate> anchors = new ArrayList<>(defaults);
      anchors.addAll(certificates);
      trustManager.set(TrustStoreLoader.trustManagerFor(anchors));
      forgetSessions();
      LOGGER.info("SMTP trusts {} certificate(s) from {} in addition to the JVM default ones",
          certificates.size(), file);
    } catch (IOException | GeneralSecurityException exp) {
      LOGGER.error("Could not load the SMTP trust store {}, {}: {}", file, outcome,
          exp.toString());
    }
  }

  private void forgetSessions() {
    if (context == null) {
      return;
    }
    // A resumed TLS session skips certificate validation, so no session validated against the
    // previous certificates may be reused.
    SSLSessionContext sessions = context.getClientSessionContext();
    for (byte[] id : Collections.list(sessions.getIds())) {
      SSLSession session = sessions.getSession(id);
      if (session != null) {
        session.invalidate();
      }
    }
  }

  private static String digest(byte[] content) {
    try {
      return new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(content)).toString(16);
    } catch (NoSuchAlgorithmException exp) {
      // Every JVM is required to provide SHA-256.
      throw new IllegalStateException(exp);
    }
  }
}
