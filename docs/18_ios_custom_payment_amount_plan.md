# iOS Custom Payment Amount

## Goal

Add a client iOS action for paying an arbitrary rental amount through the existing YooKassa payment flow.

## Plan

- [x] Extend `POST /api/v1/payments/create` with optional `amount_rub` for `payment_type = custom`.
- [x] Keep existing tariff payments unchanged for iOS and Android.
- [x] Add iOS view-model/networking support for custom amount payments.
- [x] Add the `Другая сумма` button to the tariff sheet and move tariff cards upward to make room.
- [x] Add tests for backend amount selection and iOS amount forwarding.
- [x] Run focused backend and iOS test/build verification.
