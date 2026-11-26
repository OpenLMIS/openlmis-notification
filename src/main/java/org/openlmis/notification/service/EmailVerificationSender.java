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

import static org.openlmis.notification.i18n.MessageKeys.EMAIL_VERIFICATION_EMAIL_BODY;
import static org.openlmis.notification.i18n.MessageKeys.EMAIL_VERIFICATION_EMAIL_SUBJECT;

import java.util.Locale;
import org.openlmis.notification.domain.EmailVerificationToken;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.i18n.ExposedMessageSource;
import org.openlmis.notification.service.referencedata.UserDto;
import org.openlmis.notification.service.referencedata.UserReferenceDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Sends the email verification message. Kept apart from {@link EmailVerificationNotifier} so
 * that the call goes through the async proxy: the referencedata lookup and the mail server must
 * not hold up or fail the request that created the token.
 */
@Component
public class EmailVerificationSender {

  @Autowired
  private ExposedMessageSource messageSource;

  @Autowired
  private EmailSender emailSender;

  @Autowired
  private UserReferenceDataService userReferenceDataService;

  @Value("${publicUrl}")
  private String publicUrl;

  /**
   * Sends the verification email with a link to the given token.
   */
  @Async
  public void sendEmail(UserContactDetails contactDetails, String email,
      EmailVerificationToken token, Locale locale) {
    UserDto referenceDataUser = userReferenceDataService
        .findOne(contactDetails.getReferenceDataUserId());

    String[] bodyMsgArgs = {
        referenceDataUser.getFirstName(),
        referenceDataUser.getLastName(),
        getVerificationPath(contactDetails, token)
    };
    String[] subjectMsgArgs = {};

    String subject = messageSource
        .getMessage(EMAIL_VERIFICATION_EMAIL_SUBJECT, subjectMsgArgs, locale);
    String body = messageSource
        .getMessage(EMAIL_VERIFICATION_EMAIL_BODY, bodyMsgArgs, locale);

    emailSender.sendMail(email, subject, body);
  }

  private String getVerificationPath(UserContactDetails contactDetails,
      EmailVerificationToken token) {
    return publicUrl
        + "/api/userContactDetails/"
        + contactDetails.getId()
        + "/verifications/"
        + token.getId();
  }

}
