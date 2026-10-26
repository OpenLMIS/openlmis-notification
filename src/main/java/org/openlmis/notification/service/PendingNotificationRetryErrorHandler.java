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

import static org.openlmis.notification.service.NotificationToSendRetriever.CHANNEL_TO_USE_HEADER;
import static org.openlmis.notification.service.NotificationToSendRetriever.NOTIFICATION_ID_HEADER;
import static org.openlmis.notification.service.NotificationTransformer.CHANNEL_HEADER;

import com.google.common.annotations.VisibleForTesting;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openlmis.notification.domain.PendingNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

/**
 * Error handler of the notification pollers. It runs after the poll transaction has rolled back,
 * so a failed notification is still queued; the failure is counted on it and its next attempt
 * postponed, whichever step of the flow threw. Logs one line per failure and the stack trace at
 * most once per root cause type within the configured interval. Never throws, so a failure here
 * cannot stop the poller.
 */
@Component
public class PendingNotificationRetryErrorHandler implements ErrorHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PendingNotificationRetryErrorHandler.class);

  private final PendingNotificationRetryService retryService;
  private final TraceRateLimiter traceLimiter;

  @Autowired
  public PendingNotificationRetryErrorHandler(PendingNotificationRetryService retryService,
      @Value("${notification.retry.traceIntervalSeconds}") long traceIntervalSeconds) {
    this(retryService,
        new TraceRateLimiter(Duration.ofSeconds(traceIntervalSeconds), Clock.systemUTC()));
  }

  @VisibleForTesting
  PendingNotificationRetryErrorHandler(PendingNotificationRetryService retryService,
      TraceRateLimiter traceLimiter) {
    this.retryService = retryService;
    this.traceLimiter = traceLimiter;
  }

  @Override
  public void handleError(Throwable throwable) {
    Throwable rootCause = ExceptionUtils.getRootCause(throwable);
    Throwable root = null == rootCause ? throwable : rootCause;

    String description = root.toString();

    try {
      recordFailure(throwable, root, description);
    } catch (RuntimeException ex) {
      String message = "Notification flow failed: {}, and recording the failure failed: {}";

      if (traceLimiter.allow(ex.getClass().getName())) {
        LOGGER.error(message, description, ex.toString(), ex);
      } else {
        LOGGER.warn(message, description, ex.toString());
      }
    }

    if (traceLimiter.allow(root.getClass().getName())) {
      LOGGER.error("Stack trace for {}, logged at most once per {} s",
          root.getClass().getName(), traceLimiter.getInterval().getSeconds(), throwable);
    }
  }

  private void recordFailure(Throwable throwable, Throwable root, String description) {
    Message<?> failed = throwable instanceof MessagingException
        ? ((MessagingException) throwable).getFailedMessage()
        : null;

    UUID notificationId = null == failed ? null : failed.getHeaders()
        .get(NOTIFICATION_ID_HEADER, UUID.class);
    NotificationChannel channel = null == failed ? null : getChannel(failed.getHeaders());

    if (null == notificationId || null == channel) {
      LOGGER.warn("Notification flow failed: {}", description);
      return;
    }

    PendingNotification updated = retryService.recordFailure(notificationId, channel, root);

    if (null == updated) {
      LOGGER.warn("Notification {} via {} failed and is no longer queued: {}",
          notificationId, channel, description);
    } else {
      LOGGER.warn("Notification {} via {} failed, attempt {}, next attempt at {}: {}",
          notificationId, channel, updated.getRetryCount(), updated.getRetryAt(), description);
    }
  }

  private NotificationChannel getChannel(MessageHeaders headers) {
    NotificationChannel channel = headers.get(CHANNEL_HEADER, NotificationChannel.class);
    return null == channel
        ? headers.get(CHANNEL_TO_USE_HEADER, NotificationChannel.class)
        : channel;
  }

}
