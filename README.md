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
- RabbitMQ para efectos secundarios no bloqueantes: notificaciones, documentos, auditoría y eventos.
