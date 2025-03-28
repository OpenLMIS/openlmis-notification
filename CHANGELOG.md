Upcoming Version (WIP)
==================

4.3.5 / 2025-03-31
==================

Improvements:
* [OLMIS-8065](https://openlmis.atlassian.net/browse/OLMIS-8065): Add batch APIs needed for user import functionality
* Minor coverage updates and code analysis improvements.

4.3.4 / 31.10.2024
==================

Improvements:
* [OLMIS-7895](https://openlmis.atlassian.net/browse/OLMIS-7895): Add demo data for BUQ and TB Monthly
* [OIS-14](https://openlmis.atlassian.net/browse/OIS-14): Upgrade Transifex API version
* [OIS-48](https://openlmis.atlassian.net/browse/OIS-48): Update service base images to versions without known vulnerabilities

4.3.3 / 2022-04-21
==================

Breaking changes:
* [OLMIS-7472](https://openlmis.atlassian.net/browse/OLMIS-7472): Use postgres v12

Improvement:
* [OLMIS-7568](https://openlmis.atlassian.net/browse/OLMIS-7568): Use openlmis/dev:7 and openlmis/service-base:6.1

4.3.2 / 2021-10-29
==================
Improvement:
* [OLMIS-6983](https://openlmis.atlassian.net/browse/OLMIS-6983): Sonar analysis and contract tests runs only for snapshots

4.3.1 / 2020-11-16
==================

New functionality added in a backwards-compatible manner:
* [OLMIS-6764](https://openlmis.atlassian.net/browse/OLMIS-6764): Update Spring Boot version to 2.x:
  * Update Togglz API path.

4.3.0 / 2020-04-14
==================

New functionality added in a backwards-compatible manner:
* [OLMIS-6764](https://openlmis.atlassian.net/browse/OLMIS-6764): Update Spring Boot version to 2.x:
  * Spring Boot version is 2.2.2.
  * Flyway is at 6.0.8, new mechanism for loading Spring Security for OAuth2 (matching Spring Boot version), new versions for REST Assured, RAML tester, RAML parser, PowerMock, Mockito (so tests will pass) and Java callback mechanism has changed to a general handle() method.
  * Spring application properties for Flyway have changed.
  * Fix repository method signatures (findOne is now findById, etc.); additionally they return Optional.
  * Fix unit tests.
  * Fix integration tests.
  * API definitions require "Keep-Alive" header for web integration tests.
  
Bug fixes:
* [OLMIS-6776](https://openlmis.atlassian.net/browse/OLMIS-6776): Fixed the issue with invalid token error:
  * A mechanism to retry authentication load after encountering the error was introduced.

4.2.0 / 2019-10-17
=================

New functionality:
* [OLMIS-6558](https://openlmis.atlassian.net/browse/OLMIS-6558): Add new environment variable - PUBLIC_URL and use to for email generated links

Improvements:
* [OLMIS-4128](https://openlmis.atlassian.net/browse/OLMIS-4128): Change maximum page size to max integer.
* [OLMIS-6129](https://openlmis.atlassian.net/browse/OLMIS-6129): Added package-lock.json file and removed package-lock.json entry from .gitignore.
* [OLMIS-6408](https://openlmis.atlassian.net/browse/OLMIS-6408): Added pageable validator.

Bug fixes, security and performance improvements (backwards-compatible):
* [OLMIS-6078](https://openlmis.atlassian.net/browse/OLMIS-6078): Added user contact details for admin

4.1.1 / 2019-08-23
==================

Bug fixes, security and performance improvements (backwards-compatible):
* [OLMIS-6527](https://openlmis.atlassian.net/browse/OLMIS-6527): Fixed missing localization in email address verification notification.

4.1.0 / 2019-05-27
==================

New functionality added in a backwards-compatible manner:
* [OLMIS-5859](https://openlmis.atlassian.net/browse/OLMIS-5859): Added ability to search for notifications by `userId` and date range parameters.
* [OLMIS-6016](https://openlmis.atlassian.net/browse/OLMIS-6016): Added ability to set configuration for digest notifications.
* [OLMIS-6017](https://openlmis.atlassian.net/browse/OLMIS-6017): Added ability to subscribe to get digest notifications.
* [OLMIS-6029](https://openlmis.atlassian.net/browse/OLMIS-6029): Implemented digest notification feature
* [OLMIS-6040](https://openlmis.atlassian.net/browse/OLMIS-6040): Added SMS integration to support SMS notifications.

Bug fixes, security and performance improvements (backwards-compatible):
* [OLMIS-5872](https://openlmis.atlassian.net/browse/OLMIS-5872): Added null checking for retrieving uuids in Search Params.
* [OLMIS-5971](https://openlmis.atlassian.net/browse/OLMIS-5971): Introduced `Spring Integration` to handle notifications sending
* [OLMIS-4531](https://openlmis.atlassian.net/browse/OLMIS-4531): Added compressing HTTP POST responses.

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
* [OLMIS-5866](https://openlmis.atlassian.net/browse/OLMIS-5866): Improve UUID validation

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
