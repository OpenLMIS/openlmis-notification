# Performance Data for OpenLMIS Notification Service

This folder holds the `performance-data` layer of this service's test data: contact details and
digest subscriptions of the generated referencedata users without roles.

Each data profile (`base-data`, `demo-data`, `functional-test-data`, `edge-case-data`,
`performance-data`) loads `base-data` first, then the layer it names. Rows of a layer refer only to
rows of their own layer or of `base-data`, in this service and in referencedata, whose layers these
follow.
