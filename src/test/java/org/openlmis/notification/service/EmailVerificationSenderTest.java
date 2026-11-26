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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.openlmis.notification.i18n.MessageKeys.EMAIL_VERIFICATION_EMAIL_BODY;
import static org.openlmis.notification.i18n.MessageKeys.EMAIL_VERIFICATION_EMAIL_SUBJECT;

import java.util.Locale;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.notification.domain.EmailVerificationToken;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.i18n.ExposedMessageSource;
import org.openlmis.notification.service.referencedata.UserDto;
import org.openlmis.notification.service.referencedata.UserReferenceDataService;
import org.openlmis.notification.testutils.EmailVerificationTokenDataBuilder;
import org.openlmis.notification.testutils.UserDataBuilder;
import org.openlmis.notification.util.UserContactDetailsDataBuilder;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class EmailVerificationSenderTest {

  private static final String PUBLIC_URL = "https://openlmis.example.org";

  @Mock
  private UserReferenceDataService userReferenceDataService;

  @Mock
  private EmailSender emailSender;

  @Mock
  private ExposedMessageSource messageSource;

  @InjectMocks
  private EmailVerificationSender sender;

  @Captor
  private ArgumentCaptor<String[]> bodyArgsCaptor;

  private UserDto user = new UserDataBuilder().build();
  private UserContactDetails userContactDetails = new UserContactDetailsDataBuilder()
      .withReferenceDataUserId(user.getId())
      .build();
  private EmailVerificationToken token = new EmailVerificationTokenDataBuilder()
      .withContactDetails(userContactDetails)
      .build();
  private String email = "example@test.org";

  @Before
  public void setUp() {
    ReflectionTestUtils.setField(sender, "publicUrl", PUBLIC_URL);
    when(userReferenceDataService.findOne(any(UUID.class)))
        .thenReturn(user);
    when(messageSource.getMessage(anyString(), any(String[].class), any(Locale.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class));
  }

  @Test
  public void shouldSendEmail() {
    // when
    sender.sendEmail(userContactDetails, email, token, LocaleContextHolder.getLocale());

    // then
    verify(emailSender).sendMail(email,
        EMAIL_VERIFICATION_EMAIL_SUBJECT, EMAIL_VERIFICATION_EMAIL_BODY);
  }

  @Test
  public void shouldLinkToTokenInEmailBody() {
    // when
    sender.sendEmail(userContactDetails, email, token, LocaleContextHolder.getLocale());

    // then
    verify(messageSource).getMessage(eq(EMAIL_VERIFICATION_EMAIL_BODY),
        bodyArgsCaptor.capture(), any(Locale.class));

    assertThat(bodyArgsCaptor.getValue()).containsExactly(
        user.getFirstName(),
        user.getLastName(),
        PUBLIC_URL + "/api/userContactDetails/" + userContactDetails.getId()
            + "/verifications/" + token.getId());
  }

}
