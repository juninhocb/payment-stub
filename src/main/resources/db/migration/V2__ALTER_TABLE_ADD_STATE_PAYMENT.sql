alter table payment
    add column payment_state enum (
    'NEW','AUTH','AUTH_AUTHORIZED','AUTH_ERROR','PRE_AUTH','PRE_AUTH_ERROR'
    ) default 'NEW';