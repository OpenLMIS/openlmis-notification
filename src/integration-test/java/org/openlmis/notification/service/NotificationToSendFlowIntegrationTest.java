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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.openlmis.notification.service.NotificationToSendRetriever.CHANNEL_TO_USE_HEADER;
import static org.openlmis.notification.service.NotificationToSendRetriever.IMPORTANT_HEADER;
import static org.openlmis.notification.service.NotificationToSendRetriever.RECIPIENT_HEADER;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.persistence.EntityManager;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openlmis.notification.domain.DigestConfiguration;
import org.openlmis.notification.domain.DigestSubscription;
import org.openlmis.notification.domain.Notification;
import org.openlmis.notification.domain.PendingNotification;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.repository.DigestConfigurationRepository;
import org.openlmis.notification.repository.DigestSubscriptionRepository;
import org.openlmis.notification.repository.NotificationRepository;
import org.openlmis.notification.repository.PendingNotificationRepository;
import org.openlmis.notification.repository.UserContactDetailsRepository;
import org.openlmis.notification.service.referencedata.TogglzFeatureDto;
import org.openlmis.notification.service.referencedata.TogglzReferenceDataService;
import org.openlmis.notification.testutils.DigestConfigurationDataBuilder;
import org.openlmis.notification.testutils.DigestSubscriptionDataBuilder;
import org.openlmis.notification.testutils.PendingNotificationDataBuilder;
import org.openlmis.notification.util.NotificationDataBuilder;
import org.openlmis.notification.util.UserContactDetailsDataBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles({"test", "test-run"})
@RunWith(SpringRunner.class)
@SuppressWarnings("PMD.TooManyMethods")
@SpringBootTest(properties = {"notificationToSend.autoStartup=false"})
public class NotificationToSendFlowIntegrationTest {

  private static final String SUBJECT = "subject";
  private static final String BODY = "body";

  private static final String CONFIG_TAG = "flow-integration-test-tag";

  private static final String CONFIG_MSG = "The service postpones ${count} notifications";
  private static final String EXPECTED_MSG = StringUtils.replace(CONFIG_MSG, "${count}", "1");

  private static final String CRON_EXPRESSION = "0/15 * * * * *"; // Every 15 seconds

  private static final int INITIAL_USER_CONTACT_DETAILS_COUNT = 1;

  @MockBean
  private EmailSender emailSender;

  @MockBean
  private SmsSender smsSender;

  @MockBean
  private TogglzReferenceDataService togglzReferenceDataService;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private UserContactDetailsRepository userContactDetailsRepository;

  @Autowired
  private PendingNotificationRepository pendingNotificationRepository;

  @Autowired
  private DigestConfigurationRepository digestConfigurationRepository;

  @Autowired
  private DigestSubscriptionRepository digestSubscriptionRepository;

  @Autowired
  private NotificationToSendRetriever retriever;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  @Qualifier(NotificationToSendRetriever.START_CHANNEL)
  private MessageChannel startChannel;

  private List<UserContactDetails> userContactDetails = Lists.newArrayList();
  private List<Notification> emailNotifications = Lists.newArrayList();

  private List<PendingNotification> pendingEmailNotifications = Lists.newArrayList();

  private TogglzFeatureDto digestFeature =
      new TogglzFeatureDto(DigestFilter.CONSOLIDATE_NOTIFICATIONS, true, null, null);

  private DigestConfiguration configuration;
  private List<DigestSubscription> subscriptions = Lists.newArrayList();

  private Message<Notification> message;
  private UserContactDetails correctContactDetails;
  private PendingNotification correctPendingNotification;

  private AtomicBoolean digestMessageSent = new AtomicBoolean(false);

  @Before
  public void setUp() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.execute(new DatabaseInitializer());

    given(togglzReferenceDataService.findAll()).willReturn(Lists.newArrayList(digestFeature));
  }

  @After
  public void tearDown() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.execute(new DatabaseDestroyer());
  }

  @Test
  public void shouldSendEmail() {
    // given
    digestFeature.setEnabled(false);

    // when
    startChannel.send(message);

    // then
    verify(emailSender).sendMail(correctContactDetails.getEmailAddress(), SUBJECT, BODY);

    assertThat(pendingNotificationRepository.existsById(correctPendingNotification.getId()))
        .isFalse();

    assertThat(userContactDetailsRepository.count()).isEqualTo(userContactDetails.size());
    assertThat(notificationRepository.count()).isEqualTo(emailNotifications.size());
    assertThat(pendingNotificationRepository.count())
        .isEqualTo(pendingEmailNotifications.size() - 1L);
  }

  @Test
  public void shouldSendEmailWithoutTag() {
    String subject = "Test Subject";
    String body = "Test Body";

    Notification notification = new NotificationDataBuilder()
        .withUserId(userContactDetails.get(0).getReferenceDataUserId())
        .withMessage(NotificationChannel.EMAIL, body, subject, null)
        .build();

    message = MessageBuilder.withPayload(notification)
      .setHeader(RECIPIENT_HEADER, notification.getUserId())
      .setHeader(IMPORTANT_HEADER, notification.getImportant())
      .setHeader(CHANNEL_TO_USE_HEADER, notification.getMessages().get(0).getChannel())
      .build();
    // given
    digestFeature.setEnabled(false);

    // when
    startChannel.send(message);

    // then
    verify(emailSender).sendMail(correctContactDetails.getEmailAddress(), subject, body);

    assertThat(pendingNotificationRepository.existsById(correctPendingNotification.getId()))
        .isFalse();

    assertThat(userContactDetailsRepository.count()).isEqualTo(userContactDetails.size());
    assertThat(notificationRepository.count()).isEqualTo(emailNotifications.size());
    assertThat(pendingNotificationRepository.count())
      .isEqualTo(pendingEmailNotifications.size() - 1L);

  }

  @Test
  public void shouldSendDigestEmail() {
    // given
    digestFeature.setEnabled(true);

    willAnswer(invocation -> {
      digestMessageSent.set(true);
      return null;
    }).given(emailSender).sendMail(correctContactDetails.getEmailAddress(), SUBJECT, EXPECTED_MSG);

    // when
    startChannel.send(message);

    // we have to wait at least 15 seconds because of cron expression from subscription.
    // The digest feature happens in a new thread so we have to wait until it completes the job.
    await().atMost(60, TimeUnit.SECONDS).untilTrue(digestMessageSent);

    // then
    verify(emailSender).sendMail(correctContactDetails.getEmailAddress(), SUBJECT, EXPECTED_MSG);

    assertThat(pendingNotificationRepository.existsById(correctPendingNotification.getId()))
        .isFalse();

    assertThat(userContactDetailsRepository.count())
            .isEqualTo(userContactDetails.size() + INITIAL_USER_CONTACT_DETAILS_COUNT);
    assertThat(notificationRepository.count()).isEqualTo(emailNotifications.size());
    assertThat(pendingNotificationRepository.count())
        .isEqualTo(pendingEmailNotifications.size() - 1L);
  }

  @Test
  public void shouldNotSendMessageForNonPreferredChannel() {
    Notification notification = new NotificationDataBuilder()
        .withUserId(userContactDetails.get(0).getReferenceDataUserId())
        .withMessage(NotificationChannel.SMS, "Test Body", "Test Subject",
          subscriptions.get(0).getDigestConfiguration().getTag())
        .build();

    message = MessageBuilder.withPayload(notification)
      .setHeader(RECIPIENT_HEADER, notification.getUserId())
      .setHeader(IMPORTANT_HEADER, notification.getImportant())
      .setHeader(CHANNEL_TO_USE_HEADER, notification.getMessages().get(0).getChannel())
      .build();

    // given
    digestFeature.setEnabled(false);

    // when
    startChannel.send(message);

    verify(emailSender, never()).sendMail(anyString(), anyString(), any());
    verify(smsSender, never()).sendMessage(anyString(), anyString());
  }

  @Test
  public void shouldSendEmailNotificationForTagWithoutSubscription() {
    String subject = "Test Subject";
    String body = "Test Body";

    Notification notification = new NotificationDataBuilder()
        .withUserId(userContactDetails.get(0).getReferenceDataUserId())
        .withMessage(NotificationChannel.EMAIL, body, subject, "Test Tag")
        .build();

    message = MessageBuilder.withPayload(notification)
        .setHeader(RECIPIENT_HEADER, notification.getUserId())
        .setHeader(IMPORTANT_HEADER, notification.getImportant())
        .setHeader(CHANNEL_TO_USE_HEADER, notification.getMessages().get(0).getChannel())
        .build();

    // given
    digestFeature.setEnabled(false);

    // when
    startChannel.send(message);

    verify(emailSender).sendMail(correctContactDetails.getEmailAddress(), subject, body);

    assertThat(pendingNotificationRepository.existsById(correctPendingNotification.getId()))
        .isFalse();

    assertThat(userContactDetailsRepository.count()).isEqualTo(userContactDetails.size());
    assertThat(notificationRepository.count()).isEqualTo(emailNotifications.size());
    assertThat(pendingNotificationRepository.count())
        .isEqualTo(pendingEmailNotifications.size() - 1L);
  }

  private final class DatabaseDestroyer extends TransactionCallbackWithoutResult {

    @Override
    protected void doInTransactionWithoutResult(TransactionStatus status) {
      digestSubscriptionRepository.deleteAll();
      digestConfigurationRepository.deleteAll();
      pendingNotificationRepository.deleteAll();
      notificationRepository.deleteAll();
      userContactDetailsRepository.deleteAll();

      entityManager.flush();
      entityManager.clear();
    }

  }

  private final class DatabaseInitializer extends TransactionCallbackWithoutResult {

    @Override
    protected void doInTransactionWithoutResult(TransactionStatus status) {
      createUserContactDetails();
      createEmailNotifications();
      createPendingNotifications();
      createDigestConfiguration();
      createUserSubscriptions();

      retrieveFirstPendingNotification();
    }

    private void retrieveFirstPendingNotification() {
      message = retriever.retrieve();

      correctContactDetails = userContactDetails
          .stream()
          .filter(details -> Objects.equals(message.getPayload().getUserId(), details.getId()))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Can't find correct contact details"));
      correctPendingNotification = pendingEmailNotifications
          .stream()
          .filter(item -> Objects.equals(message.getPayload().getId(), item.getNotificationId()))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Can't find correct pending notification"));
    }

    private void createUserSubscriptions() {
      subscriptions = userContactDetails
          .stream()
          .map(contactDetail -> new DigestSubscriptionDataBuilder()
              .withDigestConfiguration(configuration)
              .withUserContactDetails(contactDetail)
              .withPreferredChannel(NotificationChannel.EMAIL)
              .withUseDigest(true)
              .withCronExpression(CRON_EXPRESSION)
              .buildAsNew())
          .collect(Collectors.toList());

      digestSubscriptionRepository.saveAll(subscriptions);

      entityManager.flush();
      entityManager.clear();
    }

    private void createDigestConfiguration() {
      configuration = new DigestConfigurationDataBuilder()
          .withMessage(CONFIG_MSG)
          .withTag(CONFIG_TAG)
          .buildAsNew();

      digestConfigurationRepository.saveAndFlush(configuration);
    }

    private void createPendingNotifications() {
      pendingEmailNotifications = emailNotifications
          .stream()
          .map(notification ->
            new PendingNotificationDataBuilder().buildForEmailChannel(notification))
          .collect(Collectors.toList());

      pendingNotificationRepository.saveAll(pendingEmailNotifications);

      entityManager.flush();
      entityManager.clear();
    }

    private void createEmailNotifications() {
      emailNotifications = userContactDetails
          .stream()
          .map(contactDetail -> new NotificationDataBuilder()
              .withUserId(contactDetail.getId())
              .withMessage(NotificationChannel.EMAIL, BODY, SUBJECT, CONFIG_TAG)
              .buildAsNew())
          .collect(Collectors.toList());

      notificationRepository.saveAll(emailNotifications);

      entityManager.flush();
      entityManager.clear();
    }

    private void createUserContactDetails() {
      userContactDetails = IntStream
          .range(0, 3)
          .mapToObj(idx -> new UserContactDetailsDataBuilder().build())
          .collect(Collectors.toList());

      userContactDetailsRepository.saveAll(userContactDetails);

      entityManager.flush();
      entityManager.clear();
    }

  }
}
