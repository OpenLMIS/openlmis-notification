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

package org.openlmis.notification.web.digestconfiguration;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_DIGEST_CONFIGURATION_NOT_FOUND;

import guru.nidi.ramltester.junit.RamlMatchers;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.openlmis.notification.domain.DigestConfiguration;
import org.openlmis.notification.testutils.DigestConfigurationDataBuilder;
import org.openlmis.notification.web.BaseWebIntegrationTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class DigestConfigurationControllerIntegrationTest extends BaseWebIntegrationTest {

  private static final String RESOURCE_URL = "/api/digestConfiguration";
  private static final String ID_RESOURCE_URL = RESOURCE_URL + "/{id}";

  private DigestConfiguration configuration = new DigestConfigurationDataBuilder().build();
  private UUID configurationId = configuration.getId();

  @Before
  public void setUp() {
    given(digestConfigurationRepository.findAll(any(Pageable.class)))
        .willReturn(new PageImpl<>(Lists.newArrayList(configuration)));
    given(digestConfigurationRepository.findById(configurationId))
        .willReturn(Optional.of(configuration));
  }

  @Test
  public void shouldGetDigestConfigurations() {
    // when
    startUserRequest()
        .when()
        .get(RESOURCE_URL)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("content", hasSize(1))
        .body("content.id", hasItems(configurationId.toString()));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnUnauthorizedForGetDigestConfigurationsIfTokenIsInvalid() {
    // when
    startRequest(null)
        .when()
        .get(RESOURCE_URL)
        .then()
        .statusCode(HttpStatus.SC_UNAUTHORIZED);

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldGetDigestConfiguration() {
    // when
    startUserRequest()
        .pathParam("id", configurationId)
        .when()
        .get(ID_RESOURCE_URL)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("id", is(configurationId.toString()));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnUnauthorizedForGetDigestConfigurationIfTokenIsInvalid() {
    // when
    startRequest(null)
        .pathParam("id", configurationId)
        .when()
        .get(ID_RESOURCE_URL)
        .then()
        .statusCode(HttpStatus.SC_UNAUTHORIZED);

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnNotFoundForGetDigestConfigurationIfItDoesNotExist() {
    // given
    given(digestConfigurationRepository.findById(configurationId)).willReturn(Optional.empty());

    // when
    startUserRequest()
        .pathParam("id", configurationId)
        .when()
        .get(ID_RESOURCE_URL)
        .then()
        .statusCode(HttpStatus.SC_NOT_FOUND)
        .body(MESSAGE_KEY, is(ERROR_DIGEST_CONFIGURATION_NOT_FOUND));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }
}
