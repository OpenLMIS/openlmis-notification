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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.openlmis.notification.service.NotificationToSendRetriever.NOTIFICATION_ID_HEADER;
import static org.openlmis.notification.service.NotificationToSendRetriever.RETRY_COUNT_HEADER;
import static org.openlmis.notification.service.NotificationTransformer.CHANNEL_HEADER;

import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

public class SendFailureRetryAdviceTest {

  private static final UUID NOTIFICATION_ID = UUID.randomUUID();
  private static final String PAYLOAD = "payload";

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private NotificationSendRetryScheduler retryScheduler;

  @InjectMocks
  private SendFailureRetryAdvice advice;

  private final Exception failure = new IllegalStateException("no route to SMTP host");

  @Test
  public void shouldTakeOverFailureOfAMessageThatCameOffTheQueue() {
    // Taking the failure over is what stops the polling transaction rolling back.
    boolean handled = advice.recordFailure(queuedMessage(2), failure);

    assertThat(handled).isTrue();
    verify(retryScheduler).recordFailure(eq(NOTIFICATION_ID), eq(NotificationChannel.EMAIL),
        eq(2), any(Throwable.class));
  }

  @Test
  public void shouldTreatMissingRetryCountHeaderAsNoPreviousAttempts() {
    Message<String> message = MessageBuilder
        .withPayload(PAYLOAD)
        .setHeader(NOTIFICATION_ID_HEADER, NOTIFICATION_ID)
        .setHeader(CHANNEL_HEADER, NotificationChannel.EMAIL)
        .build();

    assertThat(advice.recordFailure(message, failure)).isTrue();

    verify(retryScheduler).recordFailure(eq(NOTIFICATION_ID), eq(NotificationChannel.EMAIL),
        eq(0), any(Throwable.class));
  }

  @Test
  public void shouldPassTheChannelTheAttemptWasMadeOn() {
    Message<String> message = MessageBuilder
        .withPayload(PAYLOAD)
        .setHeader(NOTIFICATION_ID_HEADER, NOTIFICATION_ID)
        .setHeader(CHANNEL_HEADER, NotificationChannel.SMS)
        .setHeader(RETRY_COUNT_HEADER, 0)
        .build();

    assertThat(advice.recordFailure(message, failure)).isTrue();

    verify(retryScheduler).recordFailure(eq(NOTIFICATION_ID), eq(NotificationChannel.SMS),
        eq(0), any(Throwable.class));
  }

  @Test
  public void shouldNotTakeOverFailureOfAMessageThatDidNotComeOffTheQueue() {
    Message<String> message = MessageBuilder.withPayload(PAYLOAD).build();

    assertThat(advice.recordFailure(message, failure)).isFalse();

    verifyZeroInteractions(retryScheduler);
  }

  private Message<String> queuedMessage(int retryCount) {
    return MessageBuilder
        .withPayload(PAYLOAD)
        .setHeader(NOTIFICATION_ID_HEADER, NOTIFICATION_ID)
        .setHeader(CHANNEL_HEADER, NotificationChannel.EMAIL)
        .setHeader(RETRY_COUNT_HEADER, retryCount)
        .build();
  }

}
