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
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class TraceRateLimiterTest {

  private static final Duration INTERVAL = Duration.ofMinutes(10);
  private static final Instant START = Instant.parse("2026-10-26T12:00:00Z");
  private static final String KEY = "org.springframework.mail.MailSendException";

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  private Clock clock;

  private TraceRateLimiter limiter;

  @Before
  public void setUp() {
    limiter = new TraceRateLimiter(INTERVAL, clock);
  }

  @Test
  public void shouldAllowFirstTraceForKey() {
    // given
    given(clock.instant()).willReturn(START);

    // when & then
    assertThat(limiter.allow(KEY)).isTrue();
  }

  @Test
  public void shouldSuppressSecondTraceWithinInterval() {
    // given
    given(clock.instant()).willReturn(START, START.plus(INTERVAL).minusSeconds(1));

    // when & then
    assertThat(limiter.allow(KEY)).isTrue();
    assertThat(limiter.allow(KEY)).isFalse();
  }

  @Test
  public void shouldAllowAgainAfterInterval() {
    // given
    given(clock.instant()).willReturn(START, START.plusSeconds(1), START.plus(INTERVAL));

    // when & then
    assertThat(limiter.allow(KEY)).isTrue();
    assertThat(limiter.allow(KEY)).isFalse();
    assertThat(limiter.allow(KEY)).isTrue();
  }

  @Test
  public void shouldTrackKeysIndependently() {
    // given
    given(clock.instant()).willReturn(START, START.plusSeconds(1), START.plusSeconds(2));

    // when & then
    assertThat(limiter.allow(KEY)).isTrue();
    assertThat(limiter.allow("java.net.SocketTimeoutException")).isTrue();
    assertThat(limiter.allow(KEY)).isFalse();
  }

}
