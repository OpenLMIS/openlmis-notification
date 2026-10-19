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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.openlmis.notification.repository.PendingNotificationRepository;
import org.openlmis.notification.service.PermissionService;
import org.openlmis.notification.web.MissingPermissionException;

public class UndeliveredNotificationControllerTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private PermissionService permissionService;

  @Mock
  private PendingNotificationRepository pendingNotificationRepository;

  @InjectMocks
  private UndeliveredNotificationController controller;

  @Test
  public void shouldRequeueUndeliveredNotificationsAndReportHowMany() {
    given(pendingNotificationRepository.retryUndelivered()).willReturn(7);

    RetryUndeliveredResultDto result = controller.retryUndeliveredNotifications();

    assertThat(result.getRequeued()).isEqualTo(7);
    verify(pendingNotificationRepository).retryUndelivered();
  }

  @Test
  public void shouldReportZeroWhenNothingIsUndelivered() {
    given(pendingNotificationRepository.retryUndelivered()).willReturn(0);

    assertThat(controller.retryUndeliveredNotifications().getRequeued()).isZero();
  }

  @Test
  public void shouldCheckPermissionBeforeRequeueingAnything() {
    willThrow(new MissingPermissionException())
        .given(permissionService)
        .canRetryUndeliveredNotifications();

    assertThatThrownBy(() -> controller.retryUndeliveredNotifications())
        .isInstanceOf(MissingPermissionException.class);

    verify(pendingNotificationRepository, never()).retryUndelivered();
  }

}
