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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.persistence.EntityManager;
import org.junit.Before;
import org.junit.Test;
import org.openlmis.notification.domain.Notification;
import org.openlmis.notification.domain.PendingNotification;
import org.openlmis.notification.domain.PendingNotification.PendingNotificationId;
import org.openlmis.notification.service.NotificationChannel;
import org.openlmis.notification.testutils.PendingNotificationDataBuilder;
import org.openlmis.notification.util.NotificationDataBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;

public class PendingNotificationRepositoryIntegrationTest
    extends BaseCrudRepositoryIntegrationTest<PendingNotification, PendingNotificationId> {

  private static final int COUNT = 5;

  @Autowired
  private PendingNotificationRepository repository;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private EntityManager entityManager;

  private List<PendingNotification> pendingNotifications;

  @Override
  CrudRepository<PendingNotification, PendingNotificationId> getRepository() {
    return repository;
  }

  @Override
  PendingNotification generateInstance() {
    Notification notification = new NotificationDataBuilder()
        .withEmptyMessage(NotificationChannel.EMAIL)
        .buildAsNew();

    notificationRepository.saveAndFlush(notification);

    return new PendingNotificationDataBuilder()
      .buildForEmailChannel(notification);
  }

  @Override
  protected void assertBefore(PendingNotification instance) {
    assertThat(instance.getId()).isNotNull();
  }

  @Before
  public void setUp() {
    pendingNotifications = IntStream
        .range(0, COUNT)
        .mapToObj(idx -> generateInstance())
        .peek(repository::saveAndFlush)
        .collect(Collectors.toList());

    pendingNotifications.sort(Comparator.comparing(PendingNotification::getCreatedDate));
  }

  @Test
  public void shouldNotRemoveNotificationWhenPendingNotificationWasRemoved() {
    PendingNotification pending = pendingNotifications.get(0);
    UUID notificationId = pending.getNotificationId();

    repository.deleteById(pending.getId());
    entityManager.flush();

    boolean notificationExists = notificationRepository.existsById(notificationId);
    assertThat(notificationExists).isTrue();
  }

  @Test
  public void shouldPersistRetryTracking() {
    PendingNotification pending = pendingNotifications.get(0);
    ZonedDateTime retryAt = ZonedDateTime.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS);

    pending.recordFailedAttempt(retryAt, "x");
    repository.saveAndFlush(pending);
    entityManager.clear();

    PendingNotification found = repository.findById(pending.getId()).orElse(null);
    assertThat(found).isNotNull();
    assertThat(found.getRetryCount()).isEqualTo(1);
    assertThat(found.getRetryAt().toInstant()).isEqualTo(retryAt.toInstant());
    assertThat(found.getLastError()).isEqualTo("x");
  }

  @Test
  public void shouldPollOnlyDuePendingNotificationsInDueOrder() {
    ZonedDateTime now = ZonedDateTime.now();
    PendingNotification postponed = pendingNotifications.get(0);
    postponed.recordFailedAttempt(now.plusSeconds(60), "x");
    repository.saveAndFlush(postponed);
    entityManager.clear();

    assertThat(findDue(now))
        .extracting(PendingNotification::getId)
        .containsExactlyElementsOf(ids(pendingNotifications.subList(1, COUNT)));

    assertThat(findDue(now.plusSeconds(120)))
        .extracting(PendingNotification::getId)
        .containsExactlyElementsOf(ids(rotateFirstToLast(pendingNotifications)));
  }

  private List<PendingNotification> findDue(ZonedDateTime now) {
    return entityManager
        .createNamedQuery(PendingNotification.GET_PENDING_NOTIFICATIONS_NAMED_QUERY,
            PendingNotification.class)
        .setParameter(PendingNotification.NOW_PARAMETER, now)
        .getResultList();
  }

  private List<PendingNotificationId> ids(List<PendingNotification> list) {
    return list.stream().map(PendingNotification::getId).collect(Collectors.toList());
  }

  private List<PendingNotification> rotateFirstToLast(List<PendingNotification> list) {
    List<PendingNotification> rotated = new ArrayList<>(list.subList(1, list.size()));
    rotated.add(list.get(0));
    return rotated;
  }

}
