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

package org.openlmis.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZonedDateTime;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;
import org.openlmis.notification.testutils.PendingNotificationDataBuilder;
import org.openlmis.notification.testutils.ToStringTestUtils;
import org.openlmis.notification.util.NotificationDataBuilder;
import org.springframework.test.util.ReflectionTestUtils;

public class PendingNotificationTest {

  @Test
  public void equalsContract() {
    Notification left = new NotificationDataBuilder().buildAsNew();
    Notification right = new NotificationDataBuilder().build();

    EqualsVerifier
        .forClass(PendingNotification.class)
        .withPrefabValues(Notification.class, left, right)
        .withOnlyTheseFields("id")
        .verify();
  }

  @Test
  public void shouldImplementToString() {
    ToStringTestUtils.verify(PendingNotification.class, new PendingNotification(), "notification");
  }

  @Test
  public void shouldBeDueImmediatelyWhenCreated() {
    PendingNotification pending = new PendingNotificationDataBuilder().build();

    assertThat(pending.getRetryCount()).isZero();
    assertThat(pending.getRetryAt()).isEqualTo(pending.getCreatedDate());
    assertThat(pending.getLastError()).isNull();
  }

  @Test
  public void shouldRecordFailedAttempt() {
    PendingNotification pending = new PendingNotificationDataBuilder().build();
    ZonedDateTime next = ZonedDateTime.now().plusSeconds(60);

    pending.recordFailedAttempt(next, "boom");

    assertThat(pending.getRetryCount()).isEqualTo(1);
    assertThat(pending.getRetryAt()).isEqualTo(next);
    assertThat(pending.getLastError()).isEqualTo("boom");

    pending.recordFailedAttempt(next.plusSeconds(120), "boom again");

    assertThat(pending.getRetryCount()).isEqualTo(2);
    assertThat(pending.getRetryAt()).isEqualTo(next.plusSeconds(120));
    assertThat(pending.getLastError()).isEqualTo("boom again");
  }

  @Test
  public void shouldTellWhetherOlderThanMaxAge() {
    ZonedDateTime now = ZonedDateTime.now();
    Duration maxAge = Duration.ofHours(168);
    PendingNotification old = new PendingNotificationDataBuilder().build();
    ReflectionTestUtils.setField(old, "createdDate", now.minusDays(8));
    PendingNotification fresh = new PendingNotificationDataBuilder().build();

    assertThat(old.isOlderThan(maxAge, now)).isTrue();
    assertThat(fresh.isOlderThan(maxAge, now)).isFalse();
  }

}
