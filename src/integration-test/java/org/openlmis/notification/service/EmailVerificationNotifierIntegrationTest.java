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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_SEND_MAIL_FAILURE;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.util.Lists;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openlmis.notification.domain.EmailVerificationToken;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.repository.EmailVerificationTokenRepository;
import org.openlmis.notification.repository.UserContactDetailsRepository;
import org.openlmis.notification.service.referencedata.UserReferenceDataService;
import org.openlmis.notification.testutils.EmailVerificationTokenDataBuilder;
import org.openlmis.notification.testutils.UserDataBuilder;
import org.openlmis.notification.util.UserContactDetailsDataBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@ActiveProfiles({"test", "test-run"})
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"notificationToSend.autoStartup=false"})
@SuppressWarnings("PMD.TooManyMethods")
public class EmailVerificationNotifierIntegrationTest {

  private static final String NEW_EMAIL = "verification-new@openlmis.org";
  private static final String TAKEN_EMAIL = "verification-taken@openlmis.org";
  private static final String FIRST_EMAIL = "verification-first@openlmis.org";
  private static final String SECOND_EMAIL = "verification-second@openlmis.org";
  private static final long TIMEOUT_SECONDS = 10;

  @MockBean
  private EmailSender emailSender;

  @MockBean
  private UserReferenceDataService userReferenceDataService;

  @Autowired
  private EmailVerificationNotifier notifier;

  @Autowired
  private EmailVerificationTokenRepository tokenRepository;

  @Autowired
  private UserContactDetailsRepository userContactDetailsRepository;

  private List<UserContactDetails> savedContactDetails = Lists.newArrayList();
  private UserContactDetails contactDetails;

  @Before
  public void setUp() {
    contactDetails = saveContactDetails();
    given(userReferenceDataService.findOne(any(UUID.class)))
        .willReturn(new UserDataBuilder().build());
  }

  @After
  public void tearDown() {
    for (UserContactDetails saved : savedContactDetails) {
      EmailVerificationToken token = tokenRepository.findOneByUserContactDetails(saved);
      if (null != token) {
        tokenRepository.delete(token);
      }
      userContactDetailsRepository.delete(saved);
    }
  }

  @Test
  public void shouldCreateTokenBeforeReturningAndSendEmailOnAnotherThread() {
    AtomicReference<Thread> mailThread = new AtomicReference<>();
    willAnswer(invocation -> {
      mailThread.set(Thread.currentThread());
      return null;
    }).given(emailSender).sendMail(anyString(), anyString(), anyString());

    notifier.sendNotification(contactDetails, NEW_EMAIL, Locale.ENGLISH);

    EmailVerificationToken token = tokenRepository.findOneByUserContactDetails(contactDetails);
    assertThat(token).isNotNull();
    assertThat(token.getEmailAddress()).isEqualTo(NEW_EMAIL);

    await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).until(() -> null != mailThread.get());
    assertThat(mailThread.get()).isNotSameAs(Thread.currentThread());
  }

  @Test
  public void shouldKeepTokenWhenMailFails() {
    willThrow(new ServerException(new IllegalStateException("smtp down"), ERROR_SEND_MAIL_FAILURE))
        .given(emailSender).sendMail(anyString(), anyString(), anyString());

    notifier.sendNotification(contactDetails, NEW_EMAIL, Locale.ENGLISH);

    assertThat(tokenRepository.findOneByUserContactDetails(contactDetails)).isNotNull();
    await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).untilAsserted(() ->
        verify(emailSender).sendMail(eq(NEW_EMAIL), anyString(), anyString()));
  }

  @Test
  public void shouldKeepTokenWhenUserLookupFails() {
    given(userReferenceDataService.findOne(any(UUID.class)))
        .willThrow(new IllegalStateException("referencedata down"));

    notifier.sendNotification(contactDetails, NEW_EMAIL, Locale.ENGLISH);

    assertThat(tokenRepository.findOneByUserContactDetails(contactDetails)).isNotNull();
    await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).untilAsserted(() ->
        verify(userReferenceDataService).findOne(contactDetails.getReferenceDataUserId()));
    verify(emailSender, never()).sendMail(anyString(), anyString(), anyString());
  }

  @Test
  public void shouldThrowWhenEmailIsPendingForAnotherUser() {
    UserContactDetails otherContactDetails = saveContactDetails();
    tokenRepository.save(new EmailVerificationTokenDataBuilder()
        .withoutId()
        .withContactDetails(otherContactDetails)
        .withEmail(TAKEN_EMAIL)
        .build());

    assertThatThrownBy(() ->
        notifier.sendNotification(contactDetails, TAKEN_EMAIL, Locale.ENGLISH))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasCauseInstanceOf(ConstraintViolationException.class)
        .satisfies(ex -> assertThat(((ConstraintViolationException) ex.getCause())
            .getConstraintName()).isEqualTo("unq_email_verification_tokens_emailaddress"));

    verify(emailSender, never()).sendMail(anyString(), anyString(), anyString());
  }

  @Test
  public void shouldReplaceExistingToken() {
    EmailVerificationToken existing = tokenRepository.save(new EmailVerificationTokenDataBuilder()
        .withoutId()
        .withContactDetails(contactDetails)
        .build());

    notifier.sendNotification(contactDetails, NEW_EMAIL, Locale.ENGLISH);

    EmailVerificationToken token = tokenRepository.findOneByUserContactDetails(contactDetails);
    assertThat(token).isNotNull();
    assertThat(token.getId()).isNotEqualTo(existing.getId());
    assertThat(token.getEmailAddress()).isEqualTo(NEW_EMAIL);
    await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).untilAsserted(() ->
        verify(emailSender).sendMail(eq(NEW_EMAIL), anyString(), anyString()));
  }

  @Test
  public void shouldReplaceTokenForConcurrentRequestsOfSameUser() throws Exception {
    tokenRepository.save(new EmailVerificationTokenDataBuilder()
        .withoutId()
        .withContactDetails(contactDetails)
        .build());

    assertConcurrentNotificationsSucceed();
  }

  @Test
  public void shouldCreateTokenForConcurrentRequestsOfSameUser() throws Exception {
    assertConcurrentNotificationsSucceed();
  }

  private void assertConcurrentNotificationsSucceed() throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first = executor.submit(() -> sendNotificationAfter(barrier, FIRST_EMAIL));
      Future<?> second = executor.submit(() -> sendNotificationAfter(barrier, SECOND_EMAIL));

      assertThatCode(() -> first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .doesNotThrowAnyException();
      assertThatCode(() -> second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .doesNotThrowAnyException();
    } finally {
      executor.shutdownNow();
    }

    EmailVerificationToken token = tokenRepository.findOneByUserContactDetails(contactDetails);
    assertThat(token).isNotNull();
    assertThat(token.getEmailAddress()).isIn(FIRST_EMAIL, SECOND_EMAIL);
    await().atMost(TIMEOUT_SECONDS, TimeUnit.SECONDS).untilAsserted(() ->
        verify(emailSender, times(2)).sendMail(anyString(), anyString(), anyString()));
  }

  private Void sendNotificationAfter(CyclicBarrier barrier, String emailAddress)
      throws Exception {
    barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    notifier.sendNotification(contactDetails, emailAddress, Locale.ENGLISH);
    return null;
  }

  private UserContactDetails saveContactDetails() {
    UserContactDetails saved = userContactDetailsRepository
        .save(new UserContactDetailsDataBuilder().build());
    savedContactDetails.add(saved);
    return saved;
  }
}
