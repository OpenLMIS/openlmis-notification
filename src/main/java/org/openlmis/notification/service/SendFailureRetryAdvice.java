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

import static org.openlmis.notification.service.NotificationToSendRetriever.NOTIFICATION_ID_HEADER;
import static org.openlmis.notification.service.NotificationToSendRetriever.RETRY_COUNT_HEADER;
import static org.openlmis.notification.service.NotificationTransformer.CHANNEL_HEADER;

import com.google.common.annotations.VisibleForTesting;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

/**
 * Keeps a failed delivery attempt from rolling back the polling transaction. The queue is drained
 * by a transactional poller that deletes the row it polled, so an exception escaping a send
 * handler used to roll that delete back and leave the notification to be retried, and logged,
 * once a second indefinitely. This catches it, hands it to
 * {@link NotificationSendRetryScheduler} and lets the transaction commit.
 */
@Component(SendFailureRetryAdvice.BEAN_NAME)
public class SendFailureRetryAdvice extends AbstractRequestHandlerAdvice {

  static final String BEAN_NAME = "sendFailureRetryAdvice";

  private static final Logger LOGGER = LoggerFactory.getLogger(SendFailureRetryAdvice.class);

  @Autowired
  private NotificationSendRetryScheduler retryScheduler;

  @Override
  protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message) {
    try {
      return callback.execute();
    } catch (Exception exp) {
      if (!recordFailure(message, exp)) {
        // Only unchecked exceptions can reach here: ExecutionCallback declares none.
        throw exp;
      }

      return null;
    }
  }

  /**
   * Hands a failed delivery attempt to the retry scheduler.
   *
   * @return false when the message did not come off the queue, so the caller should deal with
   *         the failure itself
   */
  @VisibleForTesting
  boolean recordFailure(Message<?> message, Exception exp) {
    MessageHeaders headers = message.getHeaders();

    UUID notificationId = headers.get(NOTIFICATION_ID_HEADER, UUID.class);
    NotificationChannel channel = headers.get(CHANNEL_HEADER, NotificationChannel.class);
    Integer failedAttempts = headers.get(RETRY_COUNT_HEADER, Integer.class);

    if (null == notificationId || null == channel) {
      // Not from the queue, so there is no entry to re-arm and no replay loop to break.
      LOGGER.debug("Send failure on a message without queue headers, rethrowing");
      return false;
    }

    retryScheduler.recordFailure(notificationId, channel,
        null == failedAttempts ? 0 : failedAttempts, unwrapExceptionIfNecessary(exp));

    return true;
  }

}
