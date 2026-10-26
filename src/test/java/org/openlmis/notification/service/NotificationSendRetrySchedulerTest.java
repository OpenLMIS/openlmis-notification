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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.openlmis.notification.repository.PendingNotificationRepository;
import org.springframework.test.util.ReflectionTestUtils;

public class NotificationSendRetrySchedulerTest {

  private static final UUID NOTIFICATION_ID = UUID.randomUUID();
  private static final int MAX_ATTEMPTS = 5;
  private static final long INITIAL_DELAY = 60L;
  private static final long MAX_DELAY = 960L;

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private PendingNotificationRepository pendingNotificationRepository;

  @InjectMocks
  private NotificationSendRetryScheduler scheduler;

  private final Exception cause = new IllegalStateException("SMTP is unhappy");

  @Before
  public void setUp() {
    setLimits(MAX_ATTEMPTS, INITIAL_DELAY, MAX_DELAY);
  }

  @Test
  public void shouldRescheduleWithInitialDelayAfterFirstFailure() {
    scheduler.recordFailure(NOTIFICATION_ID, NotificationChannel.EMAIL, 0, cause);

    verifyRescheduled(1, INITIAL_DELAY);
  }

  @Test
  public void shouldDoubleDelayAfterEachFurtherFailure() {
    scheduler.recordFailure(NOTIFICATION_ID, NotificationChannel.EMAIL, 1, cause);
    verifyRescheduled(2, 120L);

    scheduler.recordFailure(NOTIFICATION_ID, NotificationChannel.EMAIL, 2, cause);
    verifyRescheduled(3, 240L);

    scheduler.recordFailure(NOTIFICATION_ID, NotificationChannel.EMAIL, 3, cause);
    verifyRescheduled(4, 480L);
  }

  @Test
  public void shouldNotLetTheDelayGrowBeyondTheConfiguredMaximum() {
    setLimits(MAX_ATTEMPTS, INITIAL_DELAY, 100L);

    scheduler.recordFailure(NOTIFICATION_ID, NotificationChannel.EMAIL, 3, cause);

    verifyRescheduled(4, 100L);
  }

  @Test
  public void shouldMarkNotificationUndeliveredOnceTheAttemptBudgetIsExhausted() {
    scheduler.recordFailure(NOTIFICATION_ID, NotificationChannel.EMAIL, MAX_ATTEMPTS - 1, cause);

    // Marked undelivered rather than dropped, so the backlog stays retryable.
    verify(pendingNotificationRepository).markUndelivered(
        eq(NOTIFICATION_ID), eq(NotificationChannel.EMAIL.name()), eq(MAX_ATTEMPTS), anyString());
    verify(pendingNotificationRepository, never()).rescheduleAfterFailedAttempt(
        any(), anyString(), anyInt(), anyLong(), anyString());
  }

  @Test
  public void shouldRecordTheFailureCauseOnTheUndeliveredNotification() {
    scheduler.recordFailure(NOTIFICATION_ID, NotificationChannel.EMAIL, MAX_ATTEMPTS - 1, cause);

    verify(pendingNotificationRepository).markUndelivered(
        eq(NOTIFICATION_ID), eq(NotificationChannel.EMAIL.name()),
        eq(MAX_ATTEMPTS), contains("SMTP is unhappy"));
  }

  @Test
  public void shouldKeepUsingTheChannelTheAttemptWasMadeOn() {
    scheduler.recordFailure(NOTIFICATION_ID, NotificationChannel.SMS, 0, cause);

    verify(pendingNotificationRepository).rescheduleAfterFailedAttempt(
        eq(NOTIFICATION_ID), eq(NotificationChannel.SMS.name()),
        eq(1), eq(INITIAL_DELAY), anyString());
  }

  private void verifyRescheduled(int expectedRetryCount, long expectedDelaySeconds) {
    verify(pendingNotificationRepository).rescheduleAfterFailedAttempt(
        eq(NOTIFICATION_ID), eq(NotificationChannel.EMAIL.name()),
        eq(expectedRetryCount), eq(expectedDelaySeconds), anyString());
  }

  private void setLimits(int maxAttempts, long delay, long maxDelay) {
    ReflectionTestUtils.setField(scheduler, "maxAttempts", maxAttempts);
    ReflectionTestUtils.setField(scheduler, "retryDelaySeconds", delay);
    ReflectionTestUtils.setField(scheduler, "retryMaxDelaySeconds", maxDelay);
  }

}
