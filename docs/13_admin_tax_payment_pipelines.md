# Admin tax payment pipelines

Atom Go supports two admin tax modes for YooKassa payments. The mode is stamped onto each `client_rental` when it is created, because client login/password belongs to a concrete client rental period and every client payment must follow that period's tax pipeline.

## Self-employed admin (NPD)

Use this mode when the admin accepts payments as a self-employed person.

Admin account type:

```text
admin.tax_mode = SELF_EMPLOYED
```

Payment pipeline:

1. Client creates a YooKassa payment in the app.
2. Backend resolves the `client_rental` from the client's token and reads `client_rental.tax_mode`.
3. Backend sends the payment to YooKassa without a 54-FZ `receipt` object.
4. YooKassa confirms payment through webhook or backend status polling.
5. Atom Go applies the payment to the client ledger.
6. Atom Go stores:
   - `tax_mode = SELF_EMPLOYED`
   - `fiscalization_status = NPD_RECEIPT_PENDING`
7. The admin must ensure the NPD receipt is registered in "Мой налог" or through a connected bank flow.

Important: a bank auto-registration flow may see only the YooKassa payout to the admin account, not the original client payment. That can be wrong for NPD if the payout amount, date, or counterparty differs from the client's actual payment.

## Individual entrepreneur admin (IP)

Use this mode when the admin accepts payments as an individual entrepreneur and fiscalizes through YooKassa receipts.

Admin account type:

```text
admin.tax_mode = INDIVIDUAL_ENTREPRENEUR
```

Runtime receipt config:

```bash
YOOKASSA_SHOP_ID_IP=...
YOOKASSA_SECRET_KEY_IP=...
YOOKASSA_RECEIPT_TAX_SYSTEM_CODE=1
YOOKASSA_RECEIPT_VAT_CODE=1
YOOKASSA_RECEIPT_PAYMENT_MODE=full_payment
YOOKASSA_RECEIPT_PAYMENT_SUBJECT=service
```

`YOOKASSA_SHOP_ID` and `YOOKASSA_SECRET_KEY` remain reserved for the self-employed shop. IP client-rental payments must use the `_IP` credentials and must include `receipt`.

## Adding admins

Default local admins are still available when no extra config is provided:

```text
admin / admin123     -> SELF_EMPLOYED
admin_ip / adminip123 -> INDIVIDUAL_ENTREPRENEUR
```

Additional admins should be added through environment variables, not by changing Kotlin code. Use the same suffix for the admin config and its YooKassa keys:

```bash
ATOMGO_ADMINS=NEW_SELF,NEW_IP

ATOMGO_ADMIN_NEW_SELF_ID=admin-self-002
ATOMGO_ADMIN_NEW_SELF_LOGIN=Every-XD
ATOMGO_ADMIN_NEW_SELF_PASSWORD=...
ATOMGO_ADMIN_NEW_SELF_TAX_MODE=SELF_EMPLOYED
YOOKASSA_SHOP_ID_NEW_SELF=...
YOOKASSA_SECRET_KEY_NEW_SELF=...

ATOMGO_ADMIN_NEW_IP_ID=admin-ip-002
ATOMGO_ADMIN_NEW_IP_LOGIN=new_ip_admin
ATOMGO_ADMIN_NEW_IP_PASSWORD=...
ATOMGO_ADMIN_NEW_IP_TAX_MODE=INDIVIDUAL_ENTREPRENEUR
YOOKASSA_SHOP_ID_NEW_IP=...
YOOKASSA_SECRET_KEY_NEW_IP=...
```

`ATOMGO_ADMIN_<SUFFIX>_TAX_MODE` accepts:

- `SELF_EMPLOYED`
- `INDIVIDUAL_ENTREPRENEUR`
- `ip`
- `individual_entrepreneur`
- `individual-entrepreneur`

Payment creation and payment status checks resolve the admin from the `client_rental` and select YooKassa credentials by that admin login. This keeps multiple self-employed admins and multiple IP admins isolated from each other while preserving the legacy fallback variables:

- default self-employed shop: `YOOKASSA_SHOP_ID` / `YOOKASSA_SECRET_KEY`
- legacy single IP shop: `YOOKASSA_SHOP_ID_IP` / `YOOKASSA_SECRET_KEY_IP`

Payment pipeline:

1. Client creates a YooKassa payment in the app.
2. Backend resolves the `client_rental` from the client's token and reads `client_rental.tax_mode`.
3. Backend builds a 54-FZ receipt from the payment, client rental, and client phone.
4. Backend sends the payment to YooKassa with `receipt`.
5. YooKassa processes the payment and receipt according to the shop settings.
6. Atom Go stores:
   - `tax_mode = INDIVIDUAL_ENTREPRENEUR`
   - `fiscalization_status = YOOKASSA_RECEIPT_PENDING`
7. YooKassa webhook or status polling still remains the source of truth for applying money to the ledger.

If the client has no valid phone, backend rejects IP payment creation because YooKassa receipts require customer contact data.

YooKassa setup requirements:

- enable online-cash-register checks for the IP shop;
- keep the YooKassa setting that accepts the payment if the check is not delivered;
- during check test mode, YooKassa can show "check delivered to cloud cash register" in payment history even when no real email is delivered to the client;
- when YooKassa returns `receipt_registration`, Atom Go stores `fiscalization_status = YOOKASSA_RECEIPT_PENDING`;
- when YooKassa omits `receipt_registration`, Atom Go stores `fiscalization_status = FISCALIZATION_NOT_CONFIGURED` and logs a safe diagnostic line without secrets.

## Implementation status

Backend foundation is implemented:

- tax mode is stored on the admin account;
- each new client rental stores `admin_id` through its parent rental and stores `tax_mode`;
- payments inherit `tax_mode` from the client rental, not from the current environment;
- payments persist `tax_mode` and `fiscalization_status`;
- IP mode sends a YooKassa `receipt`;
- self-employed mode does not send a 54-FZ receipt and marks the payment as pending NPD receipt.

Next product steps:

- add an admin account management flow for tax mode and receipt parameters;
- add explicit NPD receipt states after manual/bank confirmation;
- add receipt identifiers or links once the actual NPD/54-FZ provider returns them.
