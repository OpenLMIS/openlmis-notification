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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ReloadableTrustManagerTest {

  private static final String AUTH_TYPE = "RSA";

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private X509ExtendedTrustManager first;

  @Mock
  private X509ExtendedTrustManager second;

  private final ReloadableTrustManager trustManager = new ReloadableTrustManager();
  private final X509Certificate[] chain = new X509Certificate[0];

  @Test
  public void shouldDelegateToTheLoadedTrustManager() throws CertificateException {
    trustManager.set(first);

    trustManager.checkServerTrusted(chain, AUTH_TYPE);

    verify(first).checkServerTrusted(chain, AUTH_TYPE);
  }

  @Test
  public void shouldUseTheNewTrustManagerAfterAReload() throws CertificateException {
    trustManager.set(first);
    trustManager.set(second);

    trustManager.checkServerTrusted(chain, AUTH_TYPE);

    // Same instance, same SSLContext, different certificates - that is the whole point.
    verify(second).checkServerTrusted(chain, AUTH_TYPE);
  }

  @Test
  public void shouldRejectHandshakesBeforeATrustStoreIsLoaded() {
    assertThatThrownBy(() -> trustManager.checkServerTrusted(chain, AUTH_TYPE))
        .isInstanceOf(CertificateException.class);
  }

  @Test
  public void shouldDelegateEveryServerCheckOverload() throws CertificateException {
    trustManager.set(first);

    trustManager.checkServerTrusted(chain, AUTH_TYPE, (SSLEngine) null);

    verify(first).checkServerTrusted(chain, AUTH_TYPE, (SSLEngine) null);
  }

  @Test
  public void shouldDelegateEveryClientCheckOverload() throws CertificateException {
    trustManager.set(first);

    trustManager.checkClientTrusted(chain, AUTH_TYPE);
    trustManager.checkClientTrusted(chain, AUTH_TYPE, (Socket) null);
    trustManager.checkClientTrusted(chain, AUTH_TYPE, (SSLEngine) null);

    verify(first).checkClientTrusted(chain, AUTH_TYPE);
    verify(first).checkClientTrusted(chain, AUTH_TYPE, (Socket) null);
    verify(first).checkClientTrusted(chain, AUTH_TYPE, (SSLEngine) null);
  }

  @Test
  public void shouldDelegateAcceptedIssuersOnceLoaded() {
    given(first.getAcceptedIssuers()).willReturn(chain);
    trustManager.set(first);

    assertThat(trustManager.getAcceptedIssuers()).isSameAs(chain);
  }

  @Test
  public void shouldRejectEveryCheckBeforeATrustStoreIsLoaded() {
    assertThatThrownBy(() -> trustManager.checkClientTrusted(chain, AUTH_TYPE))
        .isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> trustManager.checkServerTrusted(chain, AUTH_TYPE, (Socket) null))
        .isInstanceOf(CertificateException.class);
    assertThatThrownBy(() -> trustManager.checkServerTrusted(chain, AUTH_TYPE, (SSLEngine) null))
        .isInstanceOf(CertificateException.class);
  }

  @Test
  public void shouldReportNoAcceptedIssuersBeforeATrustStoreIsLoaded() {
    assertThat(trustManager.getAcceptedIssuers()).isEmpty();
  }

  @Test
  public void shouldExposeTheSocketAwareCallbacksThatCarryHostnameVerification()
      throws CertificateException {
    trustManager.set(first);

    trustManager.checkServerTrusted(chain, AUTH_TYPE, (Socket) null);

    // Delegating the socket aware overload is what preserves hostname verification.
    verify(first).checkServerTrusted(chain, AUTH_TYPE, (Socket) null);
  }

}
