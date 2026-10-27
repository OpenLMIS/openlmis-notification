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

package org.openlmis.notification.mail;

import org.apache.commons.lang3.StringUtils;

public enum TrustStoreType {
  PEM, JKS, PKCS12;

  /**
   * Parses the MAIL_TRUSTSTORE_TYPE value, ignoring case and surrounding spaces. A blank value
   * means PEM.
   *
   * @throws IllegalArgumentException if the value is none of PEM, JKS or PKCS12
   */
  public static TrustStoreType fromValue(String value) {
    if (StringUtils.isBlank(value)) {
      return PEM;
    }
    String trimmed = value.trim();
    for (TrustStoreType type : values()) {
      if (type.name().equalsIgnoreCase(trimmed)) {
        return type;
      }
    }
    throw new IllegalArgumentException(
        "MAIL_TRUSTSTORE_TYPE must be PEM, JKS or PKCS12, was: " + value);
  }
}
