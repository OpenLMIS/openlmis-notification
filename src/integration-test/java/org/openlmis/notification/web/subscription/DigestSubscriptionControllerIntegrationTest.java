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

package org.openlmis.notification.web.subscription;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyListOf;
import static org.mockito.ArgumentMatchers.anySetOf;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_DIGEST_SUBSCRIPTION_INVALID_CHANNEL_FOR_DIGEST;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_INVALID_TAG_IN_SUBSCRIPTION;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_USER_CONTACT_DETAILS_NOT_FOUND;
import static org.openlmis.notification.i18n.MessageKeys.PERMISSION_MISSING;

import com.google.common.collect.Sets;
import guru.nidi.ramltester.junit.RamlMatchers;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.openlmis.notification.domain.DigestConfiguration;
import org.openlmis.notification.domain.DigestSubscription;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.service.NotificationChannel;
import org.openlmis.notification.testutils.DigestConfigurationDataBuilder;
import org.openlmis.notification.testutils.DigestSubscriptionDataBuilder;
import org.openlmis.notification.util.UserContactDetailsDataBuilder;
import org.openlmis.notification.web.BaseWebIntegrationTest;
import org.openlmis.notification.web.MissingPermissionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@SuppressWarnings({"PMD.TooManyMethods"})
public class DigestSubscriptionControllerIntegrationTest extends BaseWebIntegrationTest {

  private static final String USER_SUBSCRIPTIONS_URL = "/api/users/{id}/subscriptions";

  private UserContactDetails userContactDetails = new UserContactDetailsDataBuilder().build();
  private DigestConfiguration configuration = new DigestConfigurationDataBuilder().build();
  private DigestSubscription subscription = new DigestSubscriptionDataBuilder()
      .withUserContactDetails(userContactDetails)
      .withDigestConfiguration(configuration)
      .build();

  private DigestSubscriptionDto subscriptionDto = new DigestSubscriptionDto();

  private UUID userId = userContactDetails.getReferenceDataUserId();
  private UUID configurationId = configuration.getId();

  @Before
  public void setUp() {
    subscription.export(subscriptionDto);

    given(userContactDetailsRepository.findById(userId))
        .willReturn(Optional.of(userContactDetails));
    given(userContactDetailsRepository.existsById(userId)).willReturn(true);
    given(digestConfigurationRepository.findAllById(Sets.newHashSet(configurationId)))
        .willReturn(Lists.newArrayList(configuration));

    given(digestSubscriptionRepository.saveAll(anyListOf(DigestSubscription.class)))
        .willAnswer(invocation -> {
          List<DigestSubscription> list = (List<DigestSubscription>) invocation.getArguments()[0];
          list.forEach(item -> item.setId(UUID.randomUUID()));

          return list;
        });
    given(digestSubscriptionRepository.getUserSubscriptions(userId))
        .willReturn(Lists.newArrayList(subscription));

    willDoNothing().given(permissionService).canManageUserSubscriptions(userId);
  }

  @Test
  public void shouldGetUserSubscriptions() {
    // when
    startUserRequest()
        .pathParam("id", userId)
        .when()
        .get(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("digestConfiguration.id",
            hasItems(subscriptionDto.getDigestConfigurationId().toString()));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnUnauthorizedForGetUserSubscriptionsIfTokenIsInvalid() {
    // when
    startRequest(null)
        .pathParam("id", userId)
        .when()
        .get(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_UNAUTHORIZED);

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnForbiddenForGetUserSubscriptionsIfUserHasNoCorrectRight() {
    // given
    MissingPermissionException exception = new MissingPermissionException("test");
    willThrow(exception).given(permissionService).canManageUserSubscriptions(userId);

    // when
    startUserRequest()
        .pathParam("id", userId)
        .when()
        .get(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_FORBIDDEN)
        .body(MESSAGE_KEY, is(PERMISSION_MISSING));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnNotFoundForGetUserSubscriptionsIfUserDoesNotExist() {
    // given
    given(userContactDetailsRepository.existsById(userId)).willReturn(false);

    // when
    startUserRequest()
        .pathParam("id", userId)
        .when()
        .get(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_NOT_FOUND)
        .body(MESSAGE_KEY, is(ERROR_USER_CONTACT_DETAILS_NOT_FOUND));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldCreateUserSubscriptions() {
    // when
    startUserRequest()
        .pathParam("id", userId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(Lists.newArrayList(subscriptionDto))
        .when()
        .post(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .body("digestConfiguration.id",
            hasItems(subscriptionDto.getDigestConfigurationId().toString()));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnBadRequestForCreateUserSubscriptionsIfRequestBodyIsInvalid() {
    // given
    given(digestConfigurationRepository.findAllById(anySetOf(UUID.class)))
        .willReturn(Lists.newArrayList());

    // when
    startUserRequest()
        .pathParam("id", userId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(Lists.newArrayList(subscriptionDto))
        .when()
        .post(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST)
        .body(MESSAGE_KEY, is(ERROR_INVALID_TAG_IN_SUBSCRIPTION));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnUnauthorizedForCreateUserSubscriptionsIfTokenIsInvalid() {
    // when
    startRequest(null)
        .pathParam("id", userId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(Lists.newArrayList(subscriptionDto))
        .when()
        .post(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_UNAUTHORIZED);

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnForbiddenForCreateUserSubscriptionsIfUserHasNoCorrectRight() {
    // given
    MissingPermissionException exception = new MissingPermissionException("test");
    willThrow(exception).given(permissionService).canManageUserSubscriptions(userId);

    // when
    startUserRequest()
        .pathParam("id", userId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(Lists.newArrayList(subscriptionDto))
        .when()
        .post(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_FORBIDDEN)
        .body(MESSAGE_KEY, is(PERMISSION_MISSING));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnNotFoundForCreateUserSubscriptionsIfUserDoesNotExist() {
    // given
    given(userContactDetailsRepository.findById(userId)).willReturn(Optional.empty());

    // when
    startUserRequest()
        .pathParam("id", userId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(Lists.newArrayList(subscriptionDto))
        .when()
        .post(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_NOT_FOUND)
        .body(MESSAGE_KEY, is(ERROR_USER_CONTACT_DETAILS_NOT_FOUND));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }

  @Test
  public void shouldReturnInvalidRequestIfUseDigestIsSetForSmsChannel() {
    // given
    new DigestSubscriptionDataBuilder()
        .withPreferredChannel(NotificationChannel.SMS)
        .withDigestConfiguration(configuration)
        .withUseDigest(false)
        .buildAsNew()
        .export(subscriptionDto);

    subscriptionDto.setUseDigest(true);

    // when
    startUserRequest()
        .pathParam("id", userId)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(Lists.newArrayList(subscriptionDto))
        .when()
        .post(USER_SUBSCRIPTIONS_URL)
        .then()
        .statusCode(HttpStatus.SC_BAD_REQUEST)
        .body(MESSAGE_KEY, is(ERROR_DIGEST_SUBSCRIPTION_INVALID_CHANNEL_FOR_DIGEST));

    // then
    assertThat(RAML_ASSERT_MESSAGE, restAssured.getLastReport(), RamlMatchers.hasNoViolations());
  }


}
