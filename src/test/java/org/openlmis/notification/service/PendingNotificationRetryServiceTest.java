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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.openlmis.notification.service.NotificationChannel.EMAIL;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.openlmis.notification.domain.PendingNotification;
import org.openlmis.notification.domain.PendingNotification.PendingNotificationId;
import org.openlmis.notification.repository.PendingNotificationRepository;
import org.openlmis.notification.testutils.PendingNotificationDataBuilder;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

public class PendingNotificationRetryServiceTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private PendingNotificationRepository repository;

  @InjectMocks
  private PendingNotificationRetryService service;

  private PendingNotification pending = new PendingNotificationDataBuilder()
      .withChannel(EMAIL)
      .build();

  @Before
  public void setUp() {
    ReflectionTestUtils.setField(service, "initialDelaySeconds", 60L);
    ReflectionTestUtils.setField(service, "maxDelaySeconds", 960L);
  }

  @Test
  public void shouldScheduleFirstRetryAfterInitialDelay() {
    // given
    given(repository.findById(pending.getId())).willReturn(Optional.of(pending));
    given(repository.save(any(PendingNotification.class))).willAnswer(returnsFirstArg());

    // when
    PendingNotification updated = service.recordFailure(pending.getNotificationId(), EMAIL,
        new MailSendException("smtp down"));

    // then
    assertThat(updated).isSameAs(pending);
    assertThat(updated.getRetryCount()).isEqualTo(1);
    assertThat(updated.getRetryAt())
        .isCloseTo(ZonedDateTime.now().plusSeconds(60), within(2, ChronoUnit.SECONDS));
    assertThat(updated.getLastError())
        .startsWith("org.springframework.mail.MailSendException")
        .contains("smtp down");
    verify(repository).save(pending);
  }

  @Test
  public void shouldDoubleDelayUpToTheMaximum() {
    assertThat(service.backoffAfter(1)).isEqualTo(Duration.ofSeconds(60));
    assertThat(service.backoffAfter(2)).isEqualTo(Duration.ofSeconds(120));
    assertThat(service.backoffAfter(3)).isEqualTo(Duration.ofSeconds(240));
    assertThat(service.backoffAfter(4)).isEqualTo(Duration.ofSeconds(480));
    assertThat(service.backoffAfter(5)).isEqualTo(Duration.ofSeconds(960));
    assertThat(service.backoffAfter(6)).isEqualTo(Duration.ofSeconds(960));
    assertThat(service.backoffAfter(40)).isEqualTo(Duration.ofSeconds(960));
  }

  @Test
  public void shouldReturnNullWhenNotificationIsNoLongerQueued() {
    // given
    given(repository.findById(new PendingNotificationId(pending.getNotificationId(), EMAIL)))
        .willReturn(Optional.empty());

    // when
    PendingNotification updated = service.recordFailure(pending.getNotificationId(), EMAIL,
        new MailSendException("smtp down"));

    // then
    assertThat(updated).isNull();
    verify(repository, never()).save(any());
  }

  @Test
  public void shouldStoreErrorOnOneLine() {
    // given
    given(repository.findById(pending.getId())).willReturn(Optional.of(pending));
    given(repository.save(any(PendingNotification.class))).willAnswer(returnsFirstArg());

    // when
    PendingNotification updated = service.recordFailure(pending.getNotificationId(), EMAIL,
        new MailSendException("Failed messages:\n  nested exception is:\n\t550 mailbox"));

    // then
    assertThat(updated.getLastError()).isEqualTo("org.springframework.mail.MailSendException:"
        + " Failed messages: nested exception is: 550 mailbox");
  }

  @Test
  public void shouldAbbreviateLongErrors() {
    // given
    given(repository.findById(pending.getId())).willReturn(Optional.of(pending));
    given(repository.save(any(PendingNotification.class))).willAnswer(returnsFirstArg());

    // when
    PendingNotification updated = service.recordFailure(pending.getNotificationId(), EMAIL,
        new MailSendException(StringUtils.repeat('x', 3000)));

    // then
    assertThat(updated.getLastError()).hasSize(2000);
  }

}
