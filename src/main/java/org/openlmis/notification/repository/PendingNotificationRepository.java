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

package org.openlmis.notification.repository;

import java.util.UUID;
import org.openlmis.notification.domain.PendingNotification;
import org.openlmis.notification.domain.PendingNotification.PendingNotificationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PendingNotificationRepository
    extends JpaRepository<PendingNotification, PendingNotificationId> {

  /**
   * Re-arms a pending notification for a later delivery attempt. Issued natively because the
   * poller has already removed and flushed the entity within the current transaction, so a JPA
   * save cannot be used; this runs after that delete and commits with it.
   */
  @Modifying
  @Query(value = "INSERT INTO notification.pending_notifications"
      + " (notificationid, channel, createddate, retrycount, retryat, lasterror)"
      + " SELECT n.id, :channel, n.createddate, :retryCount,"
      + " now() + (:delaySeconds * INTERVAL '1 second'), :lastError"
      + " FROM notification.notifications n WHERE n.id = :notificationId",
      nativeQuery = true)
  void rescheduleAfterFailedAttempt(
      @Param("notificationId") UUID notificationId,
      @Param("channel") String channel,
      @Param("retryCount") int retryCount,
      @Param("delaySeconds") long delaySeconds,
      @Param("lastError") String lastError);

  /**
   * Marks a pending notification undelivered after it has used up its attempt budget. It stays in
   * the queue table but is skipped by the poller, so it is neither retried nor lost. Issued
   * natively for the same reason as {@link #rescheduleAfterFailedAttempt}.
   */
  @Modifying
  @Query(value = "INSERT INTO notification.pending_notifications"
      + " (notificationid, channel, createddate, retrycount, retryat,"
      + " undelivered, undeliveredat, lasterror)"
      + " SELECT n.id, :channel, n.createddate, :retryCount, now(), TRUE, now(), :lastError"
      + " FROM notification.notifications n WHERE n.id = :notificationId",
      nativeQuery = true)
  void markUndelivered(
      @Param("notificationId") UUID notificationId,
      @Param("channel") String channel,
      @Param("retryCount") int retryCount,
      @Param("lastError") String lastError);

  /**
   * Puts every undelivered notification back into the queue. The attempt counter is reset, so a
   * requeued notification gets the full budget again and its back-off starts from the initial
   * delay.
   *
   * @return how many notifications were requeued
   */
  @Modifying
  @Query(value = "UPDATE notification.pending_notifications"
      + " SET undelivered = FALSE, undeliveredat = NULL, retrycount = 0, retryat = now()"
      + " WHERE undelivered = TRUE",
      nativeQuery = true)
  int retryUndelivered();

}
