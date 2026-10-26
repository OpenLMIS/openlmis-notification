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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.openlmis.notification.config.MailTrustStoreConfiguration.SSL_SOCKET_FACTORY_PROPERTY;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("PMD.TooManyMethods")
public class MailTrustStoreConfigurationTest {

  private static final String STORE = "cacerts.jks";

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private JavaMailSender unsupportedSender;

  private JavaMailSenderImpl mailSender;
  private MailTrustStoreConfiguration configuration;
  private File store;

  @Before
  public void setUp() throws Exception {
    mailSender = new JavaMailSenderImpl();
    configuration = new MailTrustStoreConfiguration();
    store = folder.newFile(STORE);
    Files.write(store.toPath(), TestTrustStores.withCertificate());
    configure(store.getAbsolutePath(), mailSender, 0);
  }

  @Test
  public void shouldInstallTheSocketFactoryWhenAStoreIsConfigured() {
    configuration.install();

    assertThat(socketFactory()).isInstanceOf(SSLSocketFactory.class);
  }

  @Test
  public void shouldKeepTheJvmDefaultWhenNoStoreIsConfigured() {
    configure("", mailSender, 0);

    configuration.install();

    assertThat(socketFactory()).isNull();
  }

  @Test
  public void shouldKeepTheJvmDefaultWhenTheStoreCannotBeRead() {
    configure(folder.getRoot().getAbsolutePath() + "/absent.jks", mailSender, 0);

    configuration.install();

    assertThat(socketFactory()).isNull();
  }

  @Test
  public void shouldKeepTheJvmDefaultWhenTheStoreHasNoTrustAnchor() throws Exception {
    Files.write(store.toPath(), TestTrustStores.withoutAnyCertificate());

    configuration.install();

    assertThat(socketFactory()).isNull();
  }

  @Test
  public void shouldNotTouchAMailSenderItCannotConfigure() {
    configure(store.getAbsolutePath(), unsupportedSender, 0);

    configuration.install();

    verifyZeroInteractions(unsupportedSender);
  }

  @Test
  public void shouldStartWatchingWhenAnIntervalIsGiven() {
    configure(store.getAbsolutePath(), mailSender, 5);

    configuration.install();

    assertThat(ReflectionTestUtils.getField(configuration, "watcher")).isNotNull();

    configuration.shutdown();
  }

  @Test
  public void shouldNotStartWatchingWhenTheIntervalIsZero() {
    configuration.install();

    assertThat(ReflectionTestUtils.getField(configuration, "watcher")).isNull();
  }

  @Test
  public void shouldSwapTheAnchorsWhenTheStoreChanges() throws Exception {
    configuration.install();
    int before = anchorCount();

    Files.write(store.toPath(), TestTrustStores.withoutAnyCertificate());
    reloadIfChanged();

    // The new contents are refused, so the anchors already in use must survive.
    assertThat(anchorCount()).isEqualTo(before);
  }

  @Test
  public void shouldIgnoreAStoreWhoseContentsHaveNotChanged() throws Exception {
    configuration.install();

    reloadIfChanged();

    assertThat(anchorCount()).isEqualTo(1);
  }

  @Test
  public void shouldSurviveTheStoreDisappearing() {
    configuration.install();

    assertThat(store.delete()).isTrue();
    reloadIfChanged();

    assertThat(anchorCount()).isEqualTo(1);
  }

  @Test
  public void shouldReportAnUnreadableStoreOnlyOnce() {
    configuration.install();
    assertThat(store.delete()).isTrue();

    reloadIfChanged();
    boolean reportedAfterFirst = reportedFlag();
    reloadIfChanged();

    assertThat(reportedAfterFirst).isTrue();
    assertThat(reportedFlag()).isTrue();
  }

  @Test
  public void shouldBeSafeToShutDownWhenNothingWasStarted() {
    configuration.install();

    configuration.shutdown();
  }

  private void configure(String path, JavaMailSender sender, long interval) {
    ReflectionTestUtils.setField(configuration, "trustStorePath", path);
    ReflectionTestUtils.setField(configuration, "trustStorePassword", "changeit");
    ReflectionTestUtils.setField(configuration, "trustStoreType", TestTrustStores.TYPE);
    ReflectionTestUtils.setField(configuration, "reloadIntervalSeconds", interval);
    ReflectionTestUtils.setField(configuration, "mailSender", sender);
  }

  private void reloadIfChanged() {
    ReflectionTestUtils.invokeMethod(configuration, "reloadIfChanged");
  }

  private boolean reportedFlag() {
    return Boolean.TRUE.equals(ReflectionTestUtils.getField(configuration, "readFailureReported"));
  }

  private int anchorCount() {
    ReloadableTrustManager manager =
        (ReloadableTrustManager) ReflectionTestUtils.getField(configuration, "trustManager");

    return manager.getAcceptedIssuers().length;
  }

  private Object socketFactory() {
    Properties properties = mailSender.getJavaMailProperties();

    return properties.get(SSL_SOCKET_FACTORY_PROPERTY);
  }

}
