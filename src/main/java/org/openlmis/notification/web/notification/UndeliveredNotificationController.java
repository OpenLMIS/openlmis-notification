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

package org.openlmis.notification.web.notification;

import org.openlmis.notification.repository.PendingNotificationRepository;
import org.openlmis.notification.service.PermissionService;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.slf4j.profiler.Profiler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Transactional
public class UndeliveredNotificationController {

  private static final XLogger XLOGGER =
      XLoggerFactory.getXLogger(UndeliveredNotificationController.class);

  @Autowired
  private PermissionService permissionService;

  @Autowired
  private PendingNotificationRepository pendingNotificationRepository;

  /**
   * Puts every undelivered notification back into the send queue, resetting the attempt counter
   * and making them due immediately, so each starts again from the first back-off delay with a
   * full budget. Intended to be called once the underlying cause has been fixed.
   *
   * @return how many notifications were requeued
   */
  @PostMapping("/notifications/undelivered/retry")
  @ResponseStatus(HttpStatus.OK)
  public RetryUndeliveredResultDto retryUndeliveredNotifications() {
    XLOGGER.entry();
    Profiler profiler = new Profiler("RETRY_UNDELIVERED_NOTIFICATIONS");
    profiler.setLogger(XLOGGER);

    profiler.start("CHECK_PERMISSION");
    permissionService.canRetryUndeliveredNotifications();

    profiler.start("RETRY_UNDELIVERED");
    int requeued = pendingNotificationRepository.retryUndelivered();

    XLOGGER.info("Requeued {} undelivered notification(s) into the send queue", requeued);

    RetryUndeliveredResultDto result = new RetryUndeliveredResultDto(requeued);

    profiler.stop().log();
    XLOGGER.exit(result);

    return result;
  }

}
