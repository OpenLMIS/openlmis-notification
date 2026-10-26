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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_SEND_MAIL_FAILURE;
import static org.openlmis.notification.service.NotificationChannel.EMAIL;
import static org.openlmis.notification.service.NotificationChannel.SMS;
import static org.openlmis.notification.service.NotificationToSendRetriever.CHANNEL_TO_USE_HEADER;
import static org.openlmis.notification.service.NotificationToSendRetriever.NOTIFICATION_ID_HEADER;
import static org.openlmis.notification.service.NotificationTransformer.CHANNEL_HEADER;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.openlmis.notification.domain.PendingNotification;
import org.openlmis.notification.testutils.PendingNotificationDataBuilder;
import org.slf4j.LoggerFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.mail.MailSendException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.transaction.CannotCreateTransactionException;

public class PendingNotificationRetryErrorHandlerTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private PendingNotificationRetryService retryService;

  @Mock
  private TraceRateLimiter traceLimiter;

  private PendingNotificationRetryErrorHandler errorHandler;

  private Logger logger = (Logger) LoggerFactory.getLogger(
      PendingNotificationRetryErrorHandler.class);
  private ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private UUID notificationId = UUID.randomUUID();
  private PendingNotification pending = new PendingNotificationDataBuilder().build();

  @Before
  public void setUp() {
    errorHandler = new PendingNotificationRetryErrorHandler(retryService, traceLimiter);
    pending.recordFailedAttempt(ZonedDateTime.now().plusSeconds(60), "x");

    appender.start();
    logger.addAppender(appender);
  }

  @After
  public void tearDown() {
    logger.detachAppender(appender);
  }

  @Test
  public void shouldRecordFailureFromPolledMessage() {
    // given
    given(retryService.recordFailure(any(), any(), any())).willReturn(pending);

    // when
    errorHandler.handleError(new MessagingException(
        messageWith(CHANNEL_TO_USE_HEADER, EMAIL),
        new ServerException(new MailSendException("x"), ERROR_SEND_MAIL_FAILURE)));

    // then
    verify(retryService)
        .recordFailure(eq(notificationId), eq(EMAIL), isA(MailSendException.class));
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(appender.list.get(0).getFormattedMessage())
        .startsWith("Notification " + notificationId)
        .contains("attempt 1");
  }

  @Test
  public void shouldLogFailedAttemptOnOneLine() {
    // given
    given(retryService.recordFailure(any(), any(), any())).willReturn(pending);

    // when
    errorHandler.handleError(new MessagingException(
        messageWith(CHANNEL_TO_USE_HEADER, EMAIL),
        new IllegalStateException("<html>\n<head><title>502 Bad Gateway</title></head>\n</html>")));

    // then
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .doesNotContain("\n")
        .endsWith("<html> <head><title>502 Bad Gateway</title></head> </html>");
  }

  @Test
  public void shouldReadChannelHeaderSetByTransformer() {
    // given
    given(retryService.recordFailure(any(), any(), any())).willReturn(pending);

    // when
    errorHandler.handleError(new MessagingException(
        messageWith(CHANNEL_HEADER, SMS), new IllegalStateException("sms down")));

    // then
    verify(retryService)
        .recordFailure(eq(notificationId), eq(SMS), isA(IllegalStateException.class));
    assertThat(appender.list).hasSize(1);
  }

  @Test
  public void shouldLogWhenNotificationIsNoLongerQueued() {
    // given
    given(retryService.recordFailure(any(), any(), any())).willReturn(null);

    // when
    errorHandler.handleError(new MessagingException(
        messageWith(CHANNEL_TO_USE_HEADER, EMAIL), new IllegalStateException("gone")));

    // then
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .contains("no longer queued");
  }

  @Test
  public void shouldOnlyLogWhenExceptionCarriesNoQueuedNotification() {
    // when
    errorHandler.handleError(new RuntimeException("db down"));

    // then
    verifyZeroInteractions(retryService);
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("db down");
  }

  @Test
  public void shouldLogTraceOnlyWhenLimiterAllows() {
    // given
    given(traceLimiter.allow(IllegalStateException.class.getName())).willReturn(false, true);
    given(traceLimiter.getInterval()).willReturn(Duration.ofMinutes(10));

    // when
    errorHandler.handleError(new RuntimeException(new IllegalStateException("poll failed")));

    // then
    assertThat(appender.list).noneMatch(event -> null != event.getThrowableProxy());

    // when
    errorHandler.handleError(new RuntimeException(new IllegalStateException("poll failed")));

    // then
    assertThat(appender.list)
        .filteredOn(event -> null != event.getThrowableProxy())
        .hasSize(1)
        .allMatch(event -> Level.ERROR.equals(event.getLevel()));
  }

  @Test
  public void shouldNotPropagateBookkeepingFailures() {
    // given
    given(retryService.recordFailure(any(), any(), any()))
        .willThrow(new IllegalStateException("bookkeeping failed"));

    // when
    errorHandler.handleError(new MessagingException(
        messageWith(CHANNEL_TO_USE_HEADER, EMAIL), new IllegalStateException("smtp down")));

    // then
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .contains("smtp down")
        .contains("bookkeeping failed");
  }

  @Test
  public void shouldLogOriginalTraceWhenBookkeepingFails() {
    // given
    given(retryService.recordFailure(any(), any(), any()))
        .willThrow(new CannotCreateTransactionException("no connection"));
    given(traceLimiter.allow(MailSendException.class.getName())).willReturn(true);
    given(traceLimiter.getInterval()).willReturn(Duration.ofMinutes(10));

    // when
    errorHandler.handleError(new MessagingException(
        messageWith(CHANNEL_TO_USE_HEADER, EMAIL),
        new ServerException(new MailSendException("x"), ERROR_SEND_MAIL_FAILURE)));

    // then
    assertThat(appender.list)
        .filteredOn(event -> null != event.getThrowableProxy())
        .hasSize(1)
        .allMatch(event -> event.getFormattedMessage()
            .startsWith("Stack trace for " + MailSendException.class.getName()));
  }

  private Message<String> messageWith(String channelHeader, NotificationChannel channel) {
    return MessageBuilder
        .withPayload("payload")
        .setHeader(NOTIFICATION_ID_HEADER, notificationId)
        .setHeader(channelHeader, channel)
        .build();
  }

}
