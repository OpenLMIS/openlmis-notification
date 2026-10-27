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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lets a stack trace through at most once per key within the interval.
 */
class TraceRateLimiter {

  private final ConcurrentMap<String, Instant> lastAllowed = new ConcurrentHashMap<>();

  private final Duration interval;
  private final Clock clock;

  TraceRateLimiter(Duration interval, Clock clock) {
    this.interval = interval;
    this.clock = clock;
  }

  boolean allow(String key) {
    Instant now = clock.instant();
    AtomicBoolean allowed = new AtomicBoolean();

    lastAllowed.compute(key, (ignored, last) -> {
      if (null == last || !now.isBefore(last.plus(interval))) {
        allowed.set(true);
        return now;
      }

      return last;
    });

    return allowed.get();
  }

  Duration getInterval() {
    return interval;
  }

}
