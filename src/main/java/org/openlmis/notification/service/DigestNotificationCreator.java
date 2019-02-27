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

import static org.openlmis.notification.service.DigestFilter.SEND_NOW_PREPARE_CHANNEL;
import static org.openlmis.notification.service.DigestionService.AGGREGATE_POSTPONE_CHANNEL;
import static org.openlmis.notification.service.NotificationToSendRetriever.RECIPIENT_HEADER;
import static org.openlmis.notification.service.NotificationTransformer.CHANNEL_HEADER;
import static org.openlmis.notification.service.PostponeMessageRetriever.CONFIGURATION_ID_HEADER;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openlmis.notification.domain.DigestConfiguration;
import org.openlmis.notification.domain.NotificationMessage;
import org.openlmis.notification.domain.PostponeMessage;
import org.openlmis.notification.repository.DigestConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.Transformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.support.MessageBuilder;

@MessageEndpoint
public class DigestNotificationCreator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DigestNotificationCreator.class);

  @Autowired
  private DigestConfigurationRepository digestConfigurationRepository;

  /**
   * Creates a digest message based on postpone messages.
   */
  @Transformer(inputChannel = AGGREGATE_POSTPONE_CHANNEL, outputChannel = SEND_NOW_PREPARE_CHANNEL)
  public Message createDigestNotification(List<PostponeMessage> postponeMessages,
      @Header(RECIPIENT_HEADER) UUID recipient,
      @Header(CONFIGURATION_ID_HEADER) UUID configurationId,
      @Header(CHANNEL_HEADER) NotificationChannel channel) {
    DigestConfiguration configuration = digestConfigurationRepository.findOne(configurationId);

    if (Objects.isNull(configuration)) {
      LOGGER.error("Can't find digest configuration with id: {}", configurationId);
      return null;
    }

    String digestMessage = StringUtils.replace(
        configuration.getMessage(), "{count}", String.valueOf(postponeMessages.size()));

    String subject = postponeMessages.get(0).getSubject();

    NotificationMessage message = new NotificationMessage(channel, digestMessage, subject);

    return MessageBuilder
        .withPayload(message)
        .setHeader(RECIPIENT_HEADER, recipient)
        .setHeader(CHANNEL_HEADER, channel)
        .build();
  }


}