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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class TrustStoreLoaderTest {

  private TrustStoreLoader loader;

  @Before
  public void setUp() {
    loader = new TrustStoreLoader(TestTrustStores.TYPE, TestTrustStores.PASSWORD);
  }

  @Test
  public void shouldLoadTrustAnchorsFromTheStore() throws Exception {
    assertThat(loader.load(TestTrustStores.withCertificate()).getAcceptedIssuers())
        .hasSize(1)
        .allSatisfy(issuer -> assertThat(issuer.getSubjectDN().getName())
            .contains("OpenLMIS Notification Test CA"));
  }

  @Test
  public void shouldRejectAStoreWithoutAnyTrustAnchor() throws Exception {
    // An empty store would reject every certificate - worse than keeping the previous one.
    assertThatThrownBy(() -> loader.load(TestTrustStores.withoutAnyCertificate()))
        .hasMessageContaining("does not contain any trust anchor");
  }

  @Test
  public void shouldRejectContentThatIsNotAStore() {
    byte[] content = "this is not a key store".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> loader.load(content)).isInstanceOf(Exception.class);
  }

  @Test
  public void shouldRejectATruncatedStore() throws Exception {
    byte[] store = TestTrustStores.withCertificate();

    // Half a file is what a reader can see while the store is being replaced in place.
    assertThatThrownBy(() -> loader.load(Arrays.copyOf(store, store.length / 2)))
        .isInstanceOf(Exception.class);
  }

  @Test
  public void shouldRejectAStoreOpenedWithTheWrongPassword() throws Exception {
    byte[] store = TestTrustStores.withCertificate();

    assertThatThrownBy(
        () -> new TrustStoreLoader(TestTrustStores.TYPE, "wrong".toCharArray()).load(store))
        .isInstanceOf(Exception.class);
  }

}
