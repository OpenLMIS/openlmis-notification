# Base Data for OpenLMIS Notification Service

This folder holds the base layer of this service's test data: contact details and digest
subscriptions of the users named in the referencedata base layer.

Each data profile (`base-data`, `demo-data`, `functional-test-data`, `edge-case-data`,
`performance-data`) loads `base-data` first, then the layer it names. Rows of a layer refer only to
rows of their own layer or of `base-data`, in this service and in referencedata, whose layers these
follow.

Email addresses: every user in every layer has an address at `example.org`, a domain reserved for
documentation (RFC 2606) that accepts no mail, so instances running this data send no email to
real people. Keep new rows on that domain; a user gets a real address only when someone sets it.
