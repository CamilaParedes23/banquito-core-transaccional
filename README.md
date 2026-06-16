# core-account-service

Microservicio del Core Bancario BanQuito V2 responsable de cuentas, saldos, movimientos, bloqueos y reservas de pagos masivos.

## Responsabilidad

Este bounded context administra:

- Cuentas de clientes.
- Saldo contable, saldo disponible y monto retenido.
- Depósitos, retiros y transferencias P2P.
- Bloqueos de cuenta.
- Historial de estados.
- Reservas de pagos masivos para integración con Switch.
- Movimientos de reserva.
- Auditoría local y outbox.

No administra clientes, sucursales, parámetros, contabilidad, documentos ni notificaciones. Esos datos pertenecen a otros microservicios.

## Puerto

```text
8085
```

## Endpoints principales

```text
POST   /api/v1/accounts
GET    /api/v1/accounts/{accountNumber}
GET    /api/v1/accounts/{accountNumber}/balance
GET    /api/v1/accounts/{accountNumber}/transactions
GET    /api/v1/accounts/by-customer/{customerUuid}
PATCH  /api/v1/accounts/{accountNumber}/status
PATCH  /api/v1/accounts/{accountNumber}/payment-settings
POST   /api/v1/accounts/{accountNumber}/blocks
PATCH  /api/v1/accounts/{accountNumber}/blocks/{blockUuid}/release
POST   /api/v1/teller/deposits
POST   /api/v1/teller/withdrawals
POST   /api/v1/accounts/transfers/p2p/beneficiary-validation
POST   /api/v1/accounts/transfers/p2p
POST   /api/v1/accounts/transactions/{transactionUuid}/reverse
POST   /api/v1/switch-core/payment-reservations
POST   /api/v1/switch-core/payment-reservations/{reservationUuid}/consume
POST   /api/v1/switch-core/payment-reservations/{reservationUuid}/release
POST   /api/v1/switch-core/payment-reservations/{reservationUuid}/reverse
POST   /api/v1/switch-core/payment-reservations/{reservationUuid}/close
POST   /api/v1/switch-core/payment-reservations/{reservationUuid}/service-fee-charge
```

## Endpoint clave para frontend

```text
GET /api/v1/accounts/by-customer/{customerUuid}?includeBalance=true
```

Cubre:

- Dashboard: tarjetas de cuentas.
- Transfers: selector de cuentas de origen.
- Accounts: listado general de cuentas.

En Kong se podrá exponer opcionalmente como ruta amigable:

```text
GET /api/v1/customers/{customerUuid}/accounts
```

redirigida internamente a este servicio.

## Ejecución local

```bash
mvn clean package
mvn spring-boot:run
```

## OpenAPI

```text
http://localhost:8085/swagger-ui.html
http://localhost:8085/api-docs
```

## Health

```text
http://localhost:8085/actuator/health
```

## Docker

```bash
docker build -t banquito/core-account-service:latest .
docker run --rm -p 8085:8085 --env-file .env banquito/core-account-service:latest
```

## Comunicación definida

- REST/OpenAPI hacia/desde sistemas externos a través de Kong.
- gRPC para comunicación interna dentro del Core.
- Outbox transaccional con dispatcher programado y llamadas gRPC internas para notificaciones y evidencias documentales; el Core no requiere RabbitMQ en esta fase.

## Seguridad y autorización agregada

El servicio valida JWT emitidos por `identity-access-service` y aplica autorización por rol, scope y propiedad del recurso.

Reglas principales:

- Backoffice (`ADMIN_SEGURIDAD`, `CAJERO`, `OPERADOR_CONTABLE`) puede consultar/operar cuentas según endpoints administrativos.
- Clientes (`CLIENTE_PERSONA`, `CLIENTE_EMPRESA`) solo pueden consultar cuentas asociadas a su `customerUuid` del token.
- P2P exige `core.account.transfer.p2p` y que la cuenta origen pertenezca al usuario autenticado.
- Reservas de pago masivo exigen scopes `core.reserve.*`; empresas solo pueden operar reservas propias y cuentas matriz propias.
- Clientes no pueden consultar cuentas por identificación ni cuentas de terceros.



## Integración Phase 1: P2P seguro y efectos posteriores

### Validación de destinatario

```text
POST /api/v1/accounts/transfers/p2p/beneficiary-validation
```

Devuelve exclusivamente existencia, estado, nombre visible del titular e institución. No expone saldos, identificación, correo ni UUID del cliente.

### Idempotencia P2P

`POST /api/v1/accounts/transfers/p2p` acepta `Idempotency-Key` con formato UUID.

- Misma clave y mismo payload: devuelve la respuesta original sin repetir débitos, créditos ni asiento.
- Misma clave con otro payload: `409 IDEMPOTENCY_KEY_REUSED`.
- Solicitud concurrente en curso: `409 IDEMPOTENCY_IN_PROGRESS`.
- Durante la transición el encabezado puede ser opcional mediante `P2P_IDEMPOTENCY_REQUIRED=false`; el frontend debe enviarlo para obtener protección real.

La protección se respalda con restricción única en `REGISTRO_IDEMPOTENCIA` y se ejecuta en la misma transacción local que P2P.

### Notificaciones y evidencia documental

Después de confirmar P2P u On-Us, el servicio registra un evento en `OUTBOX_EVENT`. Un dispatcher `@Scheduled` llama por gRPC interno a:

- `notification-service`, que persiste, deduplica y envía por SMTP a Mailpit.
- `document-service`, que registra la evidencia canónica en MongoDB.

La transferencia no se revierte por una indisponibilidad temporal de correo o documentos; el outbox reintenta hasta el máximo configurado.
