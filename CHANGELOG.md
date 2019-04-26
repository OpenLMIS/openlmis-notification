4.0.2 / 2019-04-08
==================

Bug fixes:
* [OLMIS-6128](https://openlmis.atlassian.net/browse/OLMIS-6128): Changed dev image version to 5.2 with fix for syncing with transifex.
* [OLMIS-6193](https://openlmis.atlassian.net/browse/OLMIS-6193): Fixed translations in email verification notifier.

4.0.1 / 2018-12-12
==================

Improvements:
* [OLMIS-4295](https://openlmis.atlassian.net/browse/OLMIS-4295): Updated checkstyle to use newest google style.

4.0.0 / 2018-08-16
==================

Breaking changes:
* [OLMIS-4903](https://openlmis.atlassian.net/browse/OLMIS-4903): Added user contact details resource with related endpoints
* [OLMIS-4904](https://openlmis.atlassian.net/browse/OLMIS-4904): Modified /api/notification endpoint
  * The request body has been changed.
  * The endpoint checks if user is active, has an email address and the address has been verified.
  * The endpoint has been pluralized and now it is available under /api/notifications address.
  * Added important flag for ignoring emailVerified flag and allowNotify user setting.

New functionality added in a backwards-compatible manner:
* [OLMIS-4907](https://openlmis.atlassian.net/browse/OLMIS-4907): Added Email verification functionality
  * the verification email will be sent for a new created users with email address
  * the verification email can be resent if there is a need
  * added new endpoint to get details about pending email verification
* [OLMIS-4835](https://openlmis.atlassian.net/browse/OLMIS-4835): Added ability to opt-out from notifications
  * if user contact details do not have verified email address, the allowNotify flag will be set to false,
  * if email details do not have email address, the verified flag will be unset,
  * when email address has been verified, the allowNotify flag is set to true. The flag must be unselected manually.
* [OLMIS-4923](https://openlmis.atlassian.net/browse/OLMIS-4923): Store and allow retrieval of notifications.

Improvements:
* [OLMIS-4645](https://openlmis.atlassian.net/browse/OLMIS-4645): Added Jenkinsfile
* [OLMIS-4908](https://openlmis.atlassian.net/browse/OLMIS-4908): Moved demo data contact details from the reference data service.

3.0.5 / 2018-04-24
==================

* [OLMIS-3708](https://openlmis.atlassian.net/browse/OLMIS-3708): Introduced API Console

3.0.4 / 2017-11-09
==================

Bug fixes, security and performance improvements (backwards-compatible):
* [OLMIS-3394](https://openlmis.atlassian.net/browse/OLMIS-3394): Added notification request validator
  * from, to, subject and content fields are required and if one of them will be empty the endpoint will return response with 400 status code and error message.

3.0.3 / 2017-09-01
==================

Bug fixes, security and performance improvements (backwards-compatible):
* [OLMIS-2871](https://openlmis.atlassian.net/browse/OLMIS-2871): The service now uses an Authorization header instead of an access_token request parameter when communicating with other services.
* [MW-412](https://openlmis.atlassian.net/browse/MW-412): Added support for CORS.

3.0.2 / 2017-06-23
==================

New functionality added in a backwards-compatible manner:
* [OLMIS-2611](https://openlmis.atlassian.net/browse/OLMIS-2611): Added using locale from env file.

3.0.1 / 2017-05-08
==================

Bug fixes, security and performance improvements (backwards-compatible):

* [OLMIS-1972](https://openlmis.atlassian.net/browse/OLMIS-1972): Update Postgres from 9.4 to 9.6
* Update [Docker Dev Image](https://github.com/OpenLMIS/docker-dev) for builds from v1 to v2
