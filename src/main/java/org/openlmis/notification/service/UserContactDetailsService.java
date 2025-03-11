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

import static org.openlmis.notification.i18n.MessageKeys.ERROR_USER_CONTACT_DETAILS_NOT_FOUND;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.openlmis.notification.domain.EmailDetails;
import org.openlmis.notification.domain.EmailVerificationToken;
import org.openlmis.notification.domain.UserContactDetails;
import org.openlmis.notification.i18n.MessageKeys;
import org.openlmis.notification.repository.EmailVerificationTokenRepository;
import org.openlmis.notification.repository.UserContactDetailsRepository;
import org.openlmis.notification.web.NotFoundException;
import org.openlmis.notification.web.usercontactdetails.UserContactDetailsDto;
import org.openlmis.notification.web.usercontactdetails.UserContactDetailsDtoValidator;
import org.openlmis.notification.web.usercontactdetails.UserContactDetailsResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

@Service
public class UserContactDetailsService {

  @Autowired
  private UserContactDetailsRepository userContactDetailsRepository;

  @Autowired
  private EmailVerificationNotifier emailVerificationNotifier;

  @Autowired
  private EmailVerificationTokenRepository emailVerificationTokenRepository;

  @Autowired
  private UserContactDetailsDtoValidator validator;

  @Autowired
  private PermissionService permissionService;

  @Autowired
  private UserContactDetailsService userContactDetailsService;

  /**
   * Adds a new or updates existing user's contact details.
   */
  public UserContactDetails addOrUpdate(UserContactDetails details) {
    return userContactDetailsRepository.existsById(details.getReferenceDataUserId())
        ? updateUserContactDetails(details)
        : addUserContactDetails(details);
  }

  /**
   * Saves user contact details.
   *
   * @param userDto user contact details object
   * @param successfulResults list with success results
   * @param failedResults list with failed results
   */
  public void saveUsersContactDetails(UserContactDetailsDto userDto,
      List<UserContactDetailsResponseDto.UserDetailsResponse> successfulResults,
      List<UserContactDetailsResponseDto.FailedUserDetailsResponse> failedResults
  ) {
    try {
      BindingResult bindingResult = new BeanPropertyBindingResult(userDto, "userContactDetailsDto");
      permissionService.canManageUserContactDetails(userDto.getReferenceDataUserId());

      validator.validate(userDto, bindingResult);
      List<String> errors;
      if (bindingResult.hasErrors()) {
        errors = bindingResult.getAllErrors().stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.toList());
        failedResults.add(
            new UserContactDetailsResponseDto.FailedUserDetailsResponse(
                userDto.getReferenceDataUserId(), errors));
      } else {
        UserContactDetails contactDetails = UserContactDetails
            .newUserContactDetails(userDto);
        userContactDetailsService.addOrUpdate(contactDetails);

        successfulResults.add(new UserContactDetailsResponseDto.UserDetailsResponse(
            userDto.getReferenceDataUserId()));
      }
    } catch (Exception ex) {
      String errorMessage = String.format("%s: %s", MessageKeys.ERROR_SAVING_BATCH_CONTACT_DETAILS,
          ex.getMessage());
      failedResults.add(new UserContactDetailsResponseDto.FailedUserDetailsResponse(
          userDto.getReferenceDataUserId(),
          Collections.singletonList(errorMessage)));
    }
  }

  /**
   * Deletes user contact details.
   *
   * @param userIds user ids for whom contact details will be removed
   */
  @Transactional
  public void deleteByUserIds(Set<UUID> userIds) {
    userContactDetailsRepository.deleteByUserIds(userIds);
  }

  private UserContactDetails addUserContactDetails(UserContactDetails toSave) {
    unsetEmailVerifiedFlag(toSave);
    UserContactDetails saved = userContactDetailsRepository.save(toSave);

    if (saved.hasEmailAddress()) {
      sendVerificationEmail(saved, saved.getEmailAddress());
    }

    return saved;
  }

  private void unsetEmailVerifiedFlag(UserContactDetails toSave) {
    if (null != toSave.getEmailDetails()) {
      toSave.getEmailDetails().setEmailVerified(false);
      toSave.setAllowNotify(false);
    }
  }

  private UserContactDetails updateUserContactDetails(UserContactDetails toUpdate) {
    EmailDetails newEmailDetails = checkIfEmailAddressHasBeenChanged(toUpdate);
    UserContactDetails saved = userContactDetailsRepository.save(toUpdate);

    if (null != newEmailDetails) {
      trySendVerificationEmail(newEmailDetails, saved);
    }

    return saved;
  }

  private EmailDetails checkIfEmailAddressHasBeenChanged(UserContactDetails toUpdate) {
    UserContactDetails existing = userContactDetailsRepository.findById(toUpdate.getId())
        .orElseThrow(() -> new NotFoundException(ERROR_USER_CONTACT_DETAILS_NOT_FOUND));
    EmailDetails newEmailDetails = null;

    boolean hasEmailAddress = toUpdate.hasEmailAddress();
    boolean emailAddressChanged = hasEmailAddress
        && !toUpdate.getEmailAddress().equals(existing.getEmailAddress());

    if (emailAddressChanged) {
      newEmailDetails = toUpdate.getEmailDetails();
      toUpdate.setEmailDetails(new EmailDetails(
          existing.getEmailAddress(), existing.isEmailAddressVerified()
      ));
    }

    return newEmailDetails;
  }

  private void trySendVerificationEmail(EmailDetails newEmailDetails, UserContactDetails saved) {
    EmailVerificationToken token = emailVerificationTokenRepository
        .findOneByUserContactDetails(saved);

    boolean tokenNotExists = null == token;
    boolean emailAddressChanged = !tokenNotExists
        && !token.getEmailAddress().equals(newEmailDetails.getEmail());

    if (tokenNotExists || emailAddressChanged) {
      sendVerificationEmail(saved, newEmailDetails.getEmail());
    }
  }

  private void sendVerificationEmail(UserContactDetails contactDetails, String emailAddress) {
    emailVerificationNotifier.sendNotification(contactDetails, emailAddress, 
        LocaleContextHolder.getLocale());
  }

}
