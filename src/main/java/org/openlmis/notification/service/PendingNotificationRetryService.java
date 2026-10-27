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
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openlmis.notification.domain.PendingNotification;
import org.openlmis.notification.domain.PendingNotification.PendingNotificationId;
import org.openlmis.notification.repository.PendingNotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PendingNotificationRetryService {

  private static final int MAX_LAST_ERROR_LENGTH = 2000;
  private static final int MAX_BACKOFF_EXPONENT = 20;

  @Autowired
  private PendingNotificationRepository pendingNotificationRepository;

  @Value("${notification.retry.initialDelaySeconds}")
  private long initialDelaySeconds;

  @Value("${notification.retry.maxDelaySeconds}")
  private long maxDelaySeconds;

  /**
   * Counts a failed attempt on the queued notification and postpones its next attempt.
   *
   * @return the updated pending notification, or null when it is no longer queued
   */
  @Transactional
  public PendingNotification recordFailure(UUID notificationId, NotificationChannel channel,
      Throwable rootCause) {
    PendingNotification pending = pendingNotificationRepository
        .findById(new PendingNotificationId(notificationId, channel))
        .orElse(null);

    if (null == pending) {
      return null;
    }

    pending.recordFailedAttempt(
        ZonedDateTime.now(Clock.systemUTC()).plus(backoffAfter(pending.getRetryCount() + 1)),
        describe(rootCause));

    return pendingNotificationRepository.save(pending);
  }

  @VisibleForTesting
  Duration backoffAfter(int failedAttempts) {
    int exponent = Math.min(Math.max(failedAttempts - 1, 0), MAX_BACKOFF_EXPONENT);
    return Duration.ofSeconds(Math.min(initialDelaySeconds << exponent, maxDelaySeconds));
  }

  /**
   * One-line summary of a failure for the log and the queued row. Exception messages often span
   * lines (nested mail exceptions, HTML error pages), which line-based log shipping would split.
   */
  static String describe(Throwable rootCause) {
    return StringUtils.abbreviate(
        StringUtils.normalizeSpace(rootCause.toString()), MAX_LAST_ERROR_LENGTH);
  }

}
