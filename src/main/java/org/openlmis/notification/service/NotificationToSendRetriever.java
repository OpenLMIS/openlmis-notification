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

import com.google.common.annotations.VisibleForTesting;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import javax.persistence.EntityManager;
import org.openlmis.notification.domain.Notification;
import org.openlmis.notification.domain.PendingNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.InboundChannelAdapter;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.jpa.core.JpaExecutor;
import org.springframework.integration.jpa.support.parametersource.ParameterSource;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

@MessageEndpoint
public class NotificationToSendRetriever {

  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationToSendRetriever.class);

  static final String START_CHANNEL = "notificationToSend.start";

  static final String RECIPIENT_HEADER = "recipient";
  static final String IMPORTANT_HEADER = "important";
  static final String CHANNEL_TO_USE_HEADER = "channelToUse";
  static final String NOTIFICATION_ID_HEADER = "notificationId";

  private JpaExecutor jpaExecutor;

  @Value("${notification.retry.maxAgeHours}")
  private long maxAgeHours;

  @Autowired
  public NotificationToSendRetriever(EntityManager entityManager) {
    prepareJpaExecutor(entityManager);
  }

  @VisibleForTesting
  NotificationToSendRetriever(JpaExecutor jpaExecutor) {
    this.jpaExecutor = jpaExecutor;
  }

  /**
   * Finds the first notification that should be sent.
   */
  @InboundChannelAdapter(
      channel = START_CHANNEL,
      autoStartup = "${notificationToSend.autoStartup:true}")
  public Message<Notification> retrieve() {
    Object item = jpaExecutor.poll();

    if (!(item instanceof PendingNotification)) {
      return null;
    }

    PendingNotification pending = (PendingNotification) item;

    // The poll has already deleted the row; returning null lets that delete commit.
    if (pending.isOlderThan(Duration.ofHours(maxAgeHours), ZonedDateTime.now(Clock.systemUTC()))) {
      LOGGER.warn("Dropping notification {} via {}: queued {}, older than {} h,"
              + " {} failed attempts, last error: {}",
          pending.getNotificationId(), pending.getChannel(), pending.getCreatedDate(),
          maxAgeHours, pending.getRetryCount(), pending.getLastError());
      return null;
    }

    Notification notification = pending.getNotification();

    return MessageBuilder
        .withPayload(notification)
        .setHeader(RECIPIENT_HEADER, notification.getUserId())
        .setHeader(IMPORTANT_HEADER, notification.getImportant())
        .setHeader(CHANNEL_TO_USE_HEADER, pending.getChannel())
        .setHeader(NOTIFICATION_ID_HEADER, notification.getId())
        .build();
  }

  private void prepareJpaExecutor(EntityManager entityManager) {
    jpaExecutor = new JpaExecutor(entityManager);

    jpaExecutor.setEntityClass(PendingNotification.class);
    jpaExecutor.setNamedQuery(PendingNotification.GET_PENDING_NOTIFICATIONS_NAMED_QUERY);

    jpaExecutor.setExpectSingleResult(true);
    jpaExecutor.setMaxNumberOfResults(1);

    jpaExecutor.setParameterSource(new NowParameterSource());

    jpaExecutor.setDeleteAfterPoll(true);
    jpaExecutor.setFlush(true);
    jpaExecutor.setClearOnFlush(true);
  }

  private static final class NowParameterSource implements ParameterSource {

    @Override
    public boolean hasValue(String paramName) {
      return PendingNotification.NOW_PARAMETER.equals(paramName);
    }

    @Override
    public Object getValue(String paramName) {
      return ZonedDateTime.now(Clock.systemUTC());
    }
  }
}
