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
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_SEND_MAIL_FAILURE;
import static org.openlmis.notification.service.NotificationChannel.EMAIL;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.Table;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openlmis.notification.domain.DigestConfiguration;
import org.openlmis.notification.domain.Notification;
import org.openlmis.notification.domain.PendingNotification;
import org.openlmis.notification.domain.PendingNotification.PendingNotificationId;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.repository.NotificationRepository;
import org.openlmis.notification.repository.PendingNotificationRepository;
import org.openlmis.notification.repository.UserContactDetailsRepository;
import org.openlmis.notification.service.referencedata.TogglzReferenceDataService;
import org.openlmis.notification.testutils.DigestConfigurationDataBuilder;
import org.openlmis.notification.testutils.PendingNotificationDataBuilder;
import org.openlmis.notification.util.NotificationDataBuilder;
import org.openlmis.notification.util.UserContactDetailsDataBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles({"test", "test-run"})
@RunWith(SpringRunner.class)
@SuppressWarnings("PMD.TooManyMethods")
@MockBean(SmsSender.class)
@SpringBootTest(properties = {
    "notificationToSend.autoStartup=false",
    "notification.retry.initialDelaySeconds=2",
    "notification.retry.maxDelaySeconds=4"})
public class NotificationRetryIntegrationTest {

  private static final String SUBJECT = "subject";
  private static final String BODY = "body";

  @MockBean
  private EmailSender emailSender;

  @MockBean
  private TogglzReferenceDataService togglzReferenceDataService;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private UserContactDetailsRepository userContactDetailsRepository;

  @Autowired
  private PendingNotificationRepository pendingNotificationRepository;

  @Autowired
  private DigestionService digestionService;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  @Qualifier("notificationToSendRetriever.retrieve.inboundChannelAdapter")
  private SourcePollingChannelAdapter adapter;

  private TransactionTemplate transactionTemplate;

  private UserContactDetails userA;
  private UserContactDetails userB;
  private PendingNotificationId pendingA;
  private PendingNotificationId pendingB;

  private Logger errorHandlerLogger =
      (Logger) LoggerFactory.getLogger(PendingNotificationRetryErrorHandler.class);
  private Logger retrieverLogger =
      (Logger) LoggerFactory.getLogger(NotificationToSendRetriever.class);
  private ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @Before
  public void setUp() {
    transactionTemplate = new TransactionTemplate(transactionManager);

    userA = new UserContactDetailsDataBuilder().build();
    userB = new UserContactDetailsDataBuilder().build();
    transactionTemplate.execute(status ->
        userContactDetailsRepository.saveAll(Arrays.asList(userA, userB)));

    appender.start();
    errorHandlerLogger.addAppender(appender);
    retrieverLogger.addAppender(appender);
  }

  @After
  public void tearDown() {
    adapter.stop();

    errorHandlerLogger.detachAppender(appender);
    retrieverLogger.detachAppender(appender);

    transactionTemplate.execute(status -> {
      pendingNotificationRepository.deleteAll();
      notificationRepository.deleteAll();
      userContactDetailsRepository.deleteAll();
      return null;
    });
  }

  @Test
  public void shouldBackOffFailingNotificationAndKeepSendingOthers() {
    // given
    queueNotifications(true);
    willThrow(smtpDown()).given(emailSender).sendMail(eq(emailOf(userA)), any(), any());

    // when
    adapter.start();

    // then
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
      verify(emailSender).sendMail(emailOf(userB), SUBJECT, BODY);
      assertThat(pendingNotificationRepository.existsById(pendingB)).isFalse();

      PendingNotification failing = findPending(pendingA);
      assertThat(failing.getRetryCount()).isEqualTo(1);
      assertThat(failing.getRetryAt()).isAfter(ZonedDateTime.now());
      assertThat(failing.getLastError()).contains(MailSendException.class.getName());
    });

    await().pollDelay(2500, TimeUnit.MILLISECONDS).until(() -> true);

    verify(emailSender, atMost(3)).sendMail(eq(emailOf(userA)), any(), any());
    assertThat(findPending(pendingA).getRetryCount()).isLessThanOrEqualTo(3);
  }

  @Test
  public void shouldSendOnceTheServerWorksAgain() {
    // given
    queueNotifications(true);
    willThrow(smtpDown()).given(emailSender).sendMail(eq(emailOf(userA)), any(), any());

    adapter.start();
    await().atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(findPending(pendingA).getRetryCount()).isEqualTo(1));

    // when
    reset(emailSender);

    // then
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(pendingNotificationRepository.existsById(pendingA)).isFalse();
      verify(emailSender).sendMail(emailOf(userA), SUBJECT, BODY);
    });
  }

  @Test
  public void shouldBackOffWhenAnEarlierStepFails() {
    // given
    queueNotifications(false);
    given(togglzReferenceDataService.findAll())
        .willThrow(new IllegalStateException("referencedata unavailable"));

    // when
    adapter.start();

    // then
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
      PendingNotification failing = findPending(pendingA);
      assertThat(failing.getRetryCount()).isEqualTo(1);
      assertThat(failing.getLastError()).contains("referencedata unavailable");
      verify(emailSender).sendMail(emailOf(userB), SUBJECT, BODY);
    });
    verify(emailSender, never()).sendMail(eq(emailOf(userA)), any(), any());
  }

  @Test
  public void shouldLogOneLinePerAttemptAndTheTraceOncePerErrorType() {
    // given
    queueNotifications(true);
    // The trace limiter lives as long as the context, so this test throws a type no other test
    // does, which keeps the trace count independent of test order.
    willThrow(new ServerException(new MailAuthenticationException("auth rejected"),
        ERROR_SEND_MAIL_FAILURE)).given(emailSender).sendMail(eq(emailOf(userA)), any(), any());

    // when
    adapter.start();

    // then
    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      int retryCount = findPending(pendingA).getRetryCount();
      assertThat(retryCount).isGreaterThanOrEqualTo(2);
      assertThat(appender.list)
          .filteredOn(event -> Level.WARN.equals(event.getLevel()))
          .filteredOn(event -> event.getFormattedMessage().startsWith("Notification "))
          .hasSize(retryCount);
    });

    assertThat(appender.list)
        .filteredOn(event -> null != event.getThrowableProxy())
        .hasSize(1);
  }

  @Test
  public void shouldDropNotificationOlderThanMaxAge() {
    // given
    queueNotifications(true);
    transactionTemplate.execute(status -> entityManager
        .createNativeQuery("UPDATE notification.pending_notifications"
            + " SET createddate = now() - INTERVAL '8 days'"
            + " WHERE notificationid = :notificationId")
        .setParameter("notificationId", notificationIdOf(pendingA))
        .executeUpdate());

    // when
    adapter.start();

    // then
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(pendingNotificationRepository.existsById(pendingA)).isFalse();
      verify(emailSender).sendMail(emailOf(userB), SUBJECT, BODY);
    });
    verify(emailSender, never()).sendMail(eq(emailOf(userA)), any(), any());
    assertThat(appender.list)
        .filteredOn(event -> event.getFormattedMessage().contains("Dropping notification"))
        .hasSize(1);
  }

  @Test
  public void shouldGiveDigestAdaptersTheSameErrorHandler() {
    // given
    DigestConfiguration configuration = new DigestConfigurationDataBuilder().build();
    UUID recipient = UUID.randomUUID();

    // when
    digestionService.setPollingAdapter(EMAIL, configuration, recipient, "0 0 0 1 1 ?");

    // then
    @SuppressWarnings("unchecked")
    Table<UUID, String, SourcePollingChannelAdapter> adapters =
        (Table<UUID, String, SourcePollingChannelAdapter>)
            ReflectionTestUtils.getField(digestionService, "adapters");
    SourcePollingChannelAdapter digestAdapter = adapters.get(recipient, configuration.getTag());

    try {
      assertThat(ReflectionTestUtils.getField(digestAdapter, "errorHandler"))
          .isInstanceOf(PendingNotificationRetryErrorHandler.class);
    } finally {
      digestionService.dropExistingPollingAdapters(recipient);
    }
  }

  private void queueNotifications(boolean importantA) {
    transactionTemplate.execute(status -> {
      pendingA = queue(userA, importantA);
      pendingB = queue(userB, true);
      return null;
    });
  }

  private PendingNotificationId queue(UserContactDetails user, boolean important) {
    Notification notification = new NotificationDataBuilder()
        .withUserId(user.getId())
        .withMessage(EMAIL, BODY, SUBJECT, important ? null : "retry-integration-test-tag")
        .withImportant(important)
        .buildAsNew();
    notificationRepository.saveAndFlush(notification);

    PendingNotification pending = new PendingNotificationDataBuilder()
        .buildForEmailChannel(notification);
    pendingNotificationRepository.saveAndFlush(pending);

    return pending.getId();
  }

  private PendingNotification findPending(PendingNotificationId id) {
    return pendingNotificationRepository.findById(id)
        .orElseThrow(() -> new AssertionError("Pending notification " + id + " is gone"));
  }

  private UUID notificationIdOf(PendingNotificationId id) {
    return (UUID) ReflectionTestUtils.getField(id, "notificationId");
  }

  private String emailOf(UserContactDetails user) {
    return user.getEmailAddress();
  }

  private ServerException smtpDown() {
    return new ServerException(new MailSendException("smtp down"), ERROR_SEND_MAIL_FAILURE);
  }

}
