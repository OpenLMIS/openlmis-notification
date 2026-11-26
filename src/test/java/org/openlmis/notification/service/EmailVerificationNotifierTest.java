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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.notification.domain.EmailVerificationToken;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.repository.EmailVerificationTokenRepository;
import org.openlmis.notification.repository.UserContactDetailsRepository;
import org.openlmis.notification.testutils.SaveAnswer;
import org.openlmis.notification.util.UserContactDetailsDataBuilder;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

@RunWith(MockitoJUnitRunner.class)
public class EmailVerificationNotifierTest {

  @Mock
  private EmailVerificationSender emailVerificationSender;

  @Mock
  private EmailVerificationTokenRepository emailVerificationTokenRepository;

  @Mock
  private UserContactDetailsRepository userContactDetailsRepository;

  @Mock
  private PlatformTransactionManager transactionManager;

  @InjectMocks
  private EmailVerificationNotifier notifier;

  @Captor
  private ArgumentCaptor<EmailVerificationToken> tokenCaptor;

  private UserContactDetails userContactDetails = new UserContactDetailsDataBuilder().build();
  private String email = "example@test.org";

  @Before
  public void setUp() {
    when(emailVerificationTokenRepository.findOneByUserContactDetails(userContactDetails))
        .thenReturn(null);
    when(emailVerificationTokenRepository.save(any(EmailVerificationToken.class)))
        .thenAnswer(new SaveAnswer<>());
  }

  @Test
  public void shouldRemoveOldTokenIfExist() {
    // given
    EmailVerificationToken token = new EmailVerificationToken();

    // when
    when(emailVerificationTokenRepository.findOneByUserContactDetails(userContactDetails))
        .thenReturn(token);

    notifier.createToken(userContactDetails, email);

    // then
    verify(emailVerificationTokenRepository).findOneByUserContactDetails(userContactDetails);
    verify(emailVerificationTokenRepository).delete(token);
    verify(emailVerificationTokenRepository).flush();
  }

  @Test
  public void shouldCreateTokenAndDelegateEmail() {
    // given
    Locale locale = LocaleContextHolder.getLocale();

    // when
    notifier.sendNotification(userContactDetails, email, locale);

    // then
    verify(emailVerificationTokenRepository).save(tokenCaptor.capture());

    EmailVerificationToken token = tokenCaptor.getValue();
    assertThat(token.getUserContactDetails()).isEqualTo(userContactDetails);
    assertThat(token.getEmailAddress()).isEqualTo(email);

    verify(emailVerificationSender).sendEmail(userContactDetails, email, token, locale);
  }

  @Test
  public void shouldLockContactDetailsAndSendEmailAfterCommit() {
    // given
    Locale locale = LocaleContextHolder.getLocale();
    InOrder inOrder = inOrder(userContactDetailsRepository, emailVerificationTokenRepository,
        transactionManager, emailVerificationSender);

    // when
    notifier.sendNotification(userContactDetails, email, locale);

    // then
    inOrder.verify(userContactDetailsRepository)
        .findOneForUpdate(userContactDetails.getReferenceDataUserId());
    inOrder.verify(emailVerificationTokenRepository)
        .findOneByUserContactDetails(userContactDetails);
    inOrder.verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
    inOrder.verify(transactionManager).commit(any());
    inOrder.verify(emailVerificationSender).sendEmail(any(UserContactDetails.class),
        any(String.class), any(EmailVerificationToken.class), any(Locale.class));
  }

}
