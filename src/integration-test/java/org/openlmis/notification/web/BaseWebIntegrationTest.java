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

package org.openlmis.notification.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.openlmis.notification.web.WireMockResponses.MOCK_SERVICE_CHECK_RESULT;
import static org.openlmis.notification.web.WireMockResponses.MOCK_TOKEN_REQUEST_RESPONSE;
import static org.openlmis.notification.web.WireMockResponses.MOCK_USER_CHECK_RESULT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.ObjectMapperConfig;
import com.jayway.restassured.config.RestAssuredConfig;
import com.jayway.restassured.specification.RequestSpecification;
import guru.nidi.ramltester.RamlDefinition;
import guru.nidi.ramltester.RamlLoaders;
import guru.nidi.ramltester.restassured.RestAssuredClient;
import javax.annotation.PostConstruct;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.openlmis.notification.repository.DigestConfigurationRepository;
import org.openlmis.notification.repository.DigestSubscriptionRepository;
import org.openlmis.notification.repository.PendingNotificationRepository;
import org.openlmis.notification.repository.UserContactDetailsRepository;
import org.openlmis.notification.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    properties = { "notificationToSend.autoStartup=false" },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "test-run"})
public abstract class BaseWebIntegrationTest {
  private static final String USER_ACCESS_TOKEN = "418c89c5-7f21-4cd1-a63a-38c47892b0fe";
  protected static final String USER_ACCESS_TOKEN_HEADER = "Bearer " + USER_ACCESS_TOKEN;
  private static final String SERVICE_ACCESS_TOKEN = "6d6896a5-e94c-4183-839d-911bc63174ff";
  protected static final String SERVICE_ACCESS_TOKEN_HEADER = "Bearer " + SERVICE_ACCESS_TOKEN;

  protected static final String RAML_ASSERT_MESSAGE =
      "HTTP request/response should match RAML definition.";

  protected static final String MESSAGE_KEY = "messageKey";

  protected RestAssuredClient restAssured;

  private static final RamlDefinition ramlDefinition =
      RamlLoaders.fromClasspath().load("api-definition-raml.yaml").ignoringXheaders();

  @Value("${service.url}")
  protected String baseUri;

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(80);

  @LocalServerPort
  private int randomPort;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  protected PendingNotificationRepository pendingNotificationRepository;

  @MockBean
  protected UserContactDetailsRepository userContactDetailsRepository;

  @MockBean
  protected DigestSubscriptionRepository digestSubscriptionRepository;

  @MockBean
  protected DigestConfigurationRepository digestConfigurationRepository;

  @SpyBean
  protected PermissionService permissionService;

  /**
   * Constructor for test.
   */
  protected BaseWebIntegrationTest() {
    // This mocks the auth check to always return valid admin credentials.
    wireMockRule.stubFor(post(urlEqualTo("/api/oauth/check_token"))
        .withRequestBody(equalTo("token=" + USER_ACCESS_TOKEN))
        .willReturn(aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(MOCK_USER_CHECK_RESULT)));

    // This mocks the auth check to always return valid trusted client credentials.
    wireMockRule.stubFor(post(urlEqualTo("/api/oauth/check_token"))
        .withRequestBody(equalTo("token=" + SERVICE_ACCESS_TOKEN))
        .willReturn(aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(MOCK_SERVICE_CHECK_RESULT)));

    // This mocks the auth token request response
    wireMockRule.stubFor(post(urlPathEqualTo("/api/oauth/token?grant_type=client_credentials"))
        .willReturn(aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(MOCK_TOKEN_REQUEST_RESPONSE)));
  }

  /**
   * Initialize the REST Assured client. Done here and not in the constructor, so that randomPort is
   * available.
   */
  @PostConstruct
  public void init() {

    RestAssured.baseURI = baseUri;
    RestAssured.port = randomPort;
    RestAssured.config = RestAssuredConfig.config().objectMapperConfig(
        new ObjectMapperConfig().jackson2ObjectMapperFactory((clazz, charset) -> objectMapper)
    );
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    restAssured = ramlDefinition.createRestAssured();
  }

  protected RequestSpecification startRequest() {
    return restAssured.given();
  }

  protected RequestSpecification startRequest(String token) {
    RequestSpecification request = startRequest();

    if (null != token) {
      request = request.header(HttpHeaders.AUTHORIZATION, token);
    }

    return request;
  }

  protected RequestSpecification startUserRequest() {
    return startRequest(USER_ACCESS_TOKEN_HEADER);
  }

}
