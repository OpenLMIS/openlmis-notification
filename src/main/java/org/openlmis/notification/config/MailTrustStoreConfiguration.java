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

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Gives the SMTP connection a trust store that can be replaced without restarting the service.
 * JavaMail otherwise uses the JVM default SSLContext, which caches its anchors for the life of the
 * process. When a path is configured this builds a private SSLContext over a
 * {@link ReloadableTrustManager} and watches the file; with no path, or an unreadable store, the
 * JVM default is kept.
 */
@Configuration
public class MailTrustStoreConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(MailTrustStoreConfiguration.class);

  static final String SSL_SOCKET_FACTORY_PROPERTY = "mail.smtp.ssl.socketFactory";

  private static final String DIGEST_ALGORITHM = "SHA-256";

  @Value("${mail.truststore.path}")
  private String trustStorePath;

  @Value("${mail.truststore.password}")
  private String trustStorePassword;

  @Value("${mail.truststore.type}")
  private String trustStoreType;

  @Value("${mail.truststore.reloadIntervalSeconds}")
  private long reloadIntervalSeconds;

  @Autowired
  private JavaMailSender mailSender;

  private final ReloadableTrustManager trustManager = new ReloadableTrustManager();

  private TrustStoreLoader loader;
  private Path trustStore;
  private ScheduledExecutorService watcher;

  /**
   * Digest of the contents last attempted, successfully or not. Recording attempts rather than
   * successes keeps an unloadable store from being retried, and logged, on every tick.
   */
  private volatile String attemptedDigest;

  /**
   * Whether an unreadable trust store has already been reported, so it is not complained about
   * on every tick.
   */
  private volatile boolean readFailureReported;

  /**
   * Installs the reloadable trust store, unless none is configured.
   */
  @PostConstruct
  void install() {
    if (isBlank(trustStorePath)) {
      LOGGER.info("No mail trust store configured, using the JVM default one."
          + " Certificate changes will require a restart.");
      return;
    }

    trustStore = Paths.get(trustStorePath);
    loader = new TrustStoreLoader(trustStoreType, trustStorePassword.toCharArray());

    if (!readAndSwapIn()) {
      LOGGER.error("Mail trust store {} could not be loaded, falling back to the JVM default one."
          + " Certificate changes will require a restart.", trustStore);
      return;
    }

    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {trustManager}, null);
      installSocketFactory(context);
    } catch (GeneralSecurityException | IllegalStateException exp) {
      LOGGER.error("Could not install the reloadable mail trust store, falling back to the JVM"
          + " default one. Certificate changes will require a restart.", exp);
      return;
    }

    startWatching();
  }

  /**
   * Stops the trust store watcher.
   */
  @PreDestroy
  void shutdown() {
    if (null != watcher) {
      watcher.shutdownNow();
    }
  }

  private void installSocketFactory(SSLContext context) {
    if (!(mailSender instanceof JavaMailSenderImpl)) {
      throw new IllegalStateException(
          "Expected a JavaMailSenderImpl to configure, got " + mailSender.getClass().getName());
    }

    // put(), not setProperty(): JavaMail wants the factory instance, not a string.
    ((JavaMailSenderImpl) mailSender)
        .getJavaMailProperties()
        .put(SSL_SOCKET_FACTORY_PROPERTY, context.getSocketFactory());

    LOGGER.info("SMTP connections will use the trust store at {}", trustStore);
  }

  private void startWatching() {
    if (reloadIntervalSeconds <= 0) {
      LOGGER.info("Mail trust store watching is disabled, {} was read once at start-up",
          trustStore);
      return;
    }

    watcher = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "mail-truststore-watch");
      thread.setDaemon(true);
      return thread;
    });
    watcher.scheduleWithFixedDelay(
        this::reloadIfChanged, reloadIntervalSeconds, reloadIntervalSeconds, TimeUnit.SECONDS);

    LOGGER.info("Watching {} for certificate changes every {}s", trustStore,
        reloadIntervalSeconds);
  }

  private void reloadIfChanged() {
    // Anything thrown here would silently cancel the scheduled task, so nothing is allowed out.
    try {
      byte[] content = readTrustStore();

      if (null == content || Objects.equals(attemptedDigest, digest(content))) {
        return;
      }

      LOGGER.info("Mail trust store {} changed, reloading", trustStore);

      if (!swapIn(content)) {
        LOGGER.error("Reloading the mail trust store {} failed, keeping the certificates that"
            + " are currently in use", trustStore);
      }
    } catch (RuntimeException exp) {
      LOGGER.error("Unexpected failure while checking the mail trust store {}", trustStore, exp);
    }
  }

  /**
   * Reads the trust store, or returns null when it cannot be read at all.
   */
  private byte[] readTrustStore() {
    try {
      byte[] content = Files.readAllBytes(trustStore);
      readFailureReported = false;

      return content;
    } catch (IOException exp) {
      if (!readFailureReported) {
        readFailureReported = true;

        LOGGER.warn("Could not read the mail trust store {}, keeping the certificates that are"
            + " currently in use", trustStore, exp);
      }

      return null;
    }
  }

  private boolean readAndSwapIn() {
    byte[] content = readTrustStore();

    return null != content && swapIn(content);
  }

  /**
   * Swaps the given contents in. Returns false and changes nothing but the recorded digest on
   * failure, so the certificates already in use stay in use.
   */
  private boolean swapIn(byte[] content) {
    // Recorded before the attempt, so contents that cannot be loaded are not retried every tick.
    attemptedDigest = digest(content);

    try {
      X509ExtendedTrustManager loaded = loader.load(content);
      trustManager.set(loaded);

      LOGGER.info("Loaded {} certificate(s) from the mail trust store {}",
          loaded.getAcceptedIssuers().length, trustStore);

      return true;
    } catch (GeneralSecurityException | IOException exp) {
      LOGGER.error("Could not read the mail trust store {}", trustStore, exp);

      return false;
    }
  }

  private String digest(byte[] content) {
    try {
      byte[] hash = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(content);

      return new BigInteger(1, hash).toString(16);
    } catch (NoSuchAlgorithmException exp) {
      // Every JVM is required to provide SHA-256.
      throw new IllegalStateException(DIGEST_ALGORITHM + " is not available", exp);
    }
  }

}
