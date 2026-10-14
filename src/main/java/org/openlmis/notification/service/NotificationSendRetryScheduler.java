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

import java.util.UUID;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openlmis.notification.repository.PendingNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides what happens to a notification whose delivery attempt has just failed. Each failure
 * advances an attempt counter and pushes the next eligible poll time out with an exponential
 * back-off, which takes the notification off the head of the queue and stops the poller replaying
 * it once a second. Once the budget is exhausted it is marked undelivered rather than dropped, so
 * a long outage leaves a retryable backlog.
 */
@Component
public class NotificationSendRetryScheduler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(NotificationSendRetryScheduler.class);

  @Autowired
  private PendingNotificationRepository pendingNotificationRepository;

  @Value("${notification.send.maxAttempts}")
  private int maxAttempts;

  @Value("${notification.send.retryDelaySeconds}")
  private long retryDelaySeconds;

  @Value("${notification.send.retryMaxDelaySeconds}")
  private long retryMaxDelaySeconds;

  /**
   * Records a failed delivery attempt and either re-queues the notification or marks it
   * undelivered once the attempt budget is exhausted.
   */
  public void recordFailure(UUID notificationId, NotificationChannel channel,
      int failedAttempts, Throwable cause) {
    int attempt = failedAttempts + 1;
    // The senders already log the full trace, so only a summary is added here.
    String rootCause = ExceptionUtils.getRootCauseMessage(cause);

    if (attempt >= maxAttempts) {
      pendingNotificationRepository.markUndelivered(
          notificationId, channel.name(), attempt, rootCause);

      LOGGER.error(
          "Marking notification {} over {} undelivered after {} failed attempts, last failure:"
              + " {}. It will not be retried until it is requeued, see the undelivered flag on"
              + " notification.pending_notifications.",
          notificationId, channel, attempt, rootCause);
      return;
    }

    long delaySeconds = nextDelaySeconds(attempt);

    pendingNotificationRepository.rescheduleAfterFailedAttempt(
        notificationId, channel.name(), attempt, delaySeconds, rootCause);

    LOGGER.warn(
        "Delivery of notification {} over {} failed (attempt {} of {}): {}."
            + " Next attempt in {}s.",
        notificationId, channel, attempt, maxAttempts, rootCause, delaySeconds);
  }

  /**
   * Exponential back-off, doubling per failed attempt and capped so a long outage settles at a
   * fixed retry interval.
   */
  private long nextDelaySeconds(int attempt) {
    long delay = retryDelaySeconds;

    for (int i = 1; i < attempt && delay < retryMaxDelaySeconds; ++i) {
      delay *= 2;
    }

    return Math.min(delay, retryMaxDelaySeconds);
  }

}
