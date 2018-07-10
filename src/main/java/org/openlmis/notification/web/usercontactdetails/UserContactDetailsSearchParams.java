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

package org.openlmis.notification.web.usercontactdetails;

import static java.util.Arrays.asList;
import static org.openlmis.notification.i18n.MessageKeys.ERROR_USER_CONTACT_DETAILS_SEARCH_INVALID_PARAMS;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.openlmis.notification.web.SearchParams;
import org.openlmis.notification.web.ValidationException;
import org.springframework.util.MultiValueMap;

@EqualsAndHashCode
@ToString
public final class UserContactDetailsSearchParams {
  static final String EMAIL = "email";
  private static final List<String> ALL_PARAMETERS = asList(EMAIL);

  private SearchParams queryParams;

  /**
   * Wraps map of query params into an object.
   */
  public UserContactDetailsSearchParams(MultiValueMap<String, String> queryMap) {
    queryParams = new SearchParams(queryMap);
    validate();
  }

  /**
   * Gets {@link String} for "name" key from params.
   *
   * @return String value of name or null if params doesn't contain "name" key.
   */
  public String getEmail() {
    if (!queryParams.containsKey(EMAIL)) {
      return null;
    }
    return queryParams.getFirst(EMAIL);
  }

  /**
   * Checks if query params are valid. Returns false if any provided param is not on supported list.
   */
  public void validate() {
    if (!ALL_PARAMETERS.containsAll(queryParams.keySet())) {
      throw new ValidationException(ERROR_USER_CONTACT_DETAILS_SEARCH_INVALID_PARAMS);
    }
  }
}