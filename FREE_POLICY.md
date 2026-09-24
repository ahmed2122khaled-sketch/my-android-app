# mr.x — 100% Free User Access Policy

Copyright © 2026 mr.x.

mr.x is intended to remain **100% free for users**. Core social features must not require payment. There is no mandatory subscription, trial period, paid unlock, or in-app purchase requirement.

The project must not introduce a timer that turns a normal account into a paid account or blocks ordinary access because the user has not paid.

## Technical guardrails

- No Google Play Billing dependency.
- No `com.android.vending.BILLING` permission.
- No payment/purchase SDK required for core operation.
- No premium/paid/trial gate controls ordinary access.
- Supabase Realtime subscriptions are technical networking functionality, not paid subscriptions.

## Infrastructure

Hosting, database, storage, bandwidth, and other services can have costs for the project owner. Those costs do not automatically become a payment requirement for ordinary mr.x users.


## Enforcement
The application source is checked by `tools/verify-free.py`. A future build must not pass the project verification suite if ordinary access is gated by payment, trial expiry, premium status, or mandatory subscription.
