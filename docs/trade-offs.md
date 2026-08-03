# Trade-offs

Known gaps between what this repo does today and what a production system would need —
documented here as a deliberate call, not a silent one.

### No gRPC for service-to-service communication

`refund-service` calls `taxpayer-service` (access checks) and `ai-service` (predictions,
guidance) over plain HTTP/JSON via Spring's `RestClient` — see
[`TaxpayerClient`](../refund-service/src/main/java/com/turbotax/refund/client/TaxpayerClient.java)
and [`AiClient`](../refund-service/src/main/java/com/turbotax/refund/client/AiClient.java). No
gRPC/protobuf anywhere.

**What that costs**: no compile-time contract between services — a field rename or type change
on one side surfaces as a runtime error, not a build failure; no generated client stubs, so
each cross-service call is a hand-written wrapper; no HTTP/2 multiplexing or streaming, only
plain request/response.

**Why not yet**: at 4 services, REST is simpler to run and debug end-to-end — every call is
curl-able, visible in Swagger, and needs no proto toolchain. Distributed tracing already
threads cleanly through HTTP headers. gRPC would start paying for itself with more services,
higher call volume, or an actual need for streaming — none of which apply here yet.

### No API Gateway

The [architecture diagram](../README.md#architecture) shows an "API Gateway" in front of all
4 services — that's a presentation simplification. The real routing mechanism is Vite's
dev-server proxy (`frontend/vite.config.ts`), which only works because the React SPA is the
one and only client. A mobile app or third-party integration would have nowhere to route
through today.

### Shared Postgres instance

`auth-service`, `taxpayer-service`, and `ai-service` all connect to the same Postgres instance
and database (`turbotax`) — different tables, same server. Each service still only touches its
own tables, but there's no infrastructure boundary enforcing that, and a single Postgres
outage takes down 3 of the 4 services at once.

### No sharding strategy

Neither datastore has a plan for horizontal data partitioning as volume grows. Postgres is a
single unsharded instance (see above — shared across 3 services, let alone split further).
DynamoDB partitions automatically by `taxpayerId`, which is fine for even distribution today,
but there's no fallback (e.g. a random/compound key suffix) if one taxpayer's filing count
ever became a genuine hot-partition outlier. "Scales independently" describes the service
shape, not a tested plan for either database running out of room.

### No notification service (Email / App Push / SMS)

`refund-service` publishes `filing.created` and `refund.status.updated` to Kafka, but nothing
consumes either topic to actually notify a user their status changed — see
[`FilingEventProducer`](../refund-service/src/main/java/com/turbotax/refund/kafka/producer/FilingEventProducer.java).
A taxpayer only finds out by opening the app and reading the page. These events exist as the
intended integration point, but the notification service itself — and its email/push/SMS
provider integrations — was never built.

**What that costs**: no email, mobile push, or SMS ever reaches a user, no matter how their
refund status changes.

**Why not yet**: the two Kafka topics were built anticipating this consumer, but building the
notification service, its provider integrations (SES/SendGrid, FCM/APNs, Twilio-style SMS),
and per-user notification preferences was out of scope for this demo.
