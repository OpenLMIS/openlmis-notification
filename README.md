# OpenLMIS Notification Service
This repository holds the files for OpenLMIS Notification Independent Service.

## Prerequisites
* Docker 1.11+
* Docker Compose 1.6+

## Quick Start
1. Fork/clone this repository from GitHub.

 ```shell
 git clone https://github.com/OpenLMIS/openlmis-notification.git
 ```
2. Add an environment file called `.env` to the root folder of the project, with the required 
project settings and credentials. For a starter environment file, you can use [this 
one](https://raw.githubusercontent.com/OpenLMIS/openlmis-ref-distro/master/settings-sample.env). e.g.

 ```shell
 cd openlmis-notification
 curl -o .env -L https://raw.githubusercontent.com/OpenLMIS/openlmis-ref-distro/master/settings-sample.env
 ```
3. Develop w/ Docker by running `docker-compose run --service-ports notification`.
See [Developing w/ Docker](#devdocker).
4. You should now be in an interactive shell inside the newly created development 
environment, start the Service with: `gradle bootRun`
5. Go to `http://<yourDockerIPAddress>:8080/` to see the service name 
and version. Note that you can determine yourDockerIPAddress by running `docker-machine ip`.
6. Go to `http://<yourDockerIPAddress>:8080/api/` to see the APIs.

## Building & Testing
See the Building & Testing section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#building.

## <a name="devdocker">Developing with Docker</a>
See the Developing with Docker section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#devdocker.

### Development Environment
See the Development Environment section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#devenv.

### Build Deployment Image
See the Build Deployment Image section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#buildimage.

### Publish to Docker Repository
TODO

### Docker's file details
See the Docker's file details section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#dockerfiles.

### Running complete application with nginx proxy
See the Running complete application with nginx proxy section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#nginx.

### Logging
See the Logging section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#logging.

### Internationalization (i18n)
See the Internationalization section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#internationalization.

### Debugging
See the Debugging section in the Service Template README at
https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#debugging.

## Environment variables

Environment variables common to all services are listed here: https://github.com/OpenLMIS/openlmis-template-service/blob/master/README.md#environment-variables

This service also uses the following variables:

* **MAIL_HOST** - The hostname or IP of the SMTP server that will be used for sending outgoing email. For example: smtp.example.com. This variable has to be defined in order to send emails. 
* **MAIL_PORT** - The SMTP port to use for sending outgoing email. Usually 465 with MAIL_SMTP_SECURITY=ssl, and 587 or 25 with MAIL_SMTP_SECURITY=starttls. This variable has to be defined in order to send emails. The default is 25.
* **MAIL_SMTP_SECURITY** - How the connection to the SMTP server is secured: `ssl` (implicit TLS: the connection starts encrypted) or `starttls` (the connection starts in plain text and is upgraded with STARTTLS). With `starttls` the upgrade is required: if the server does not offer STARTTLS, sending fails, and mail is never sent in plain text. In both modes the service logs in only if the server offers AUTH. The default is `ssl`.
* **MAIL_TRUSTSTORE_PATH** - Path to a file with certificates to trust for SMTP connections, for a mail server with a self-signed certificate or one from a private CA. The certificates are trusted in addition to the JVM default ones, so a server with a public certificate is still accepted, and they apply to SMTP only: calls to other services and the SMS API keep the JVM default trust. They are used with both values of MAIL_SMTP_SECURITY. If the file is missing or cannot be loaded, the service logs an error and SMTP trusts only the JVM default certificates. When it is set, SMTP uses the service's own TLS socket factory: a custom `mail.smtp.socketFactory.class` is not used, and no client certificate from `-Djavax.net.ssl.keyStore` is presented. By default no file is used.
* **MAIL_TRUSTSTORE_TYPE** - The format of MAIL_TRUSTSTORE_PATH: `PEM`, `JKS` or `PKCS12`. A PEM file may hold several certificates. The default is `PEM`.
* **MAIL_TRUSTSTORE_PASSWORD** - The password of a JKS or PKCS12 file. Not used for PEM. The default is empty.
* **MAIL_TRUSTSTORE_RELOAD_INTERVAL_SECONDS** - How often, in seconds, to check MAIL_TRUSTSTORE_PATH for changes. A changed file is loaded without a restart. If it cannot be loaded, the error is logged once and the certificates in use stay in use. The default is 0, which turns checking off: the file is read once at startup.
* **MAIL_USERNAME** - The SMTP username to use for sending outgoing email. Usually required by the SMTP server.  
* **MAIL_PASSWORD** - The SMTP password to use for sending outgoing email. Usually required by the SMTP server.
* **MAIL_ADDRESS** - The sender email address that will be used for sending all outgoing email messages (the from-address field). For example set it to noreply@mydomain.org in order for users to see that as the sender of the email they receive. Note that some email providers (like Gmail) might overwrite this value with details from your account.   

To move from a JVM-wide trust store (`-Djavax.net.ssl.trustStore` in JAVA_OPTS, built from the JVM's CAs plus the mail server's certificate), mount the mail server's certificate as a PEM file, point MAIL_TRUSTSTORE_PATH at it, then remove the flag and whatever built that store. Keeping the flag is harmless: the certificates it adds count as JVM defaults. Failures to read or load the file are logged as errors. The certificates loaded, and each reload, are logged at INFO by `org.openlmis.notification.mail`; with a logback root level of WARN, set `logging.level.org.openlmis.notification.mail=INFO` to see them.

See [SMS integration](#sms-integration) below for environment variables for SMS integration.

## Production by Spring Profile

By default when this service is started, it will clean its schema in the database before migrating
it. This is meant for use during the normal development cycle. For production data, this obviously
is not desired as it would remove all of the production data. To change the default clean & migrate
behavior to just be a migrate behavior (which is still desired for production use), we use a Spring
Profile named `production`. To use this profile, it must be marked as Active. The easiest way to
do so is to add to the .env file:

```java
spring_profiles_active=production
```

This will set the similarly named environment variable and limit the profile in use.  The
expected use-case for this is when this service is deployed through the
[Reference Distribution](https://github.com/openlmis/openlmis-ref-distro).

### Demo Data
A basic set of demo data is included with this service, defined under 
`./src/main/resources/db/demo-data/`.  This data may be optionally loaded by using the `demo-data` 
Spring Profile.  Setting this profile may be done by setting the `spring.profiles.active` 
environment variable.

When building locally from the development environment, you may run:

```shell
$ export spring_profiles_active=demo-data
$ gradle bootRun
```

To see how to set environment variables through Docker Compose, see the 
[Reference Distribution](https://github.com/openlmis/openlmis-ref-distro)

## <a name="sms-integration">SMS Integration</a>

The Notification Service now has integration with SMS through an SMS provider. This integration has 
been designed with RapidPro/TextIt in mind, but as long as the SMS provider provides access to a 
REST API (using a token) from which to send SMS messages, it should be supported.

To enable SMS integration, two settings need to be set in the .env file:

* **SMS_SEND_API_URL** - The REST API URL of the SMS provider that will be used for sending SMS 
messages. (Example: https://textit.in/api/v2/broadcasts.json) This variable has to be defined in 
order to send SMS messages. 
* **SMS_SEND_API_TOKEN** - The API access token to use for the REST API. This variable has to be 
defined in order to send SMS messages.

Note: make sure that all users that are supposed to receive SMS messages have phone numbers set in 
their user contact details, and that these numbers are in E.164 format (e.g. for US numbers, 
12065551234).
