# Guía rápida frontend - core-account-service

## Endpoint recomendado para listar cuentas de backoffice

`GET /api/v1/accounts`

Uso recomendado en pantallas administrativas de cuentas. Siempre consumir por Kong:

`http://localhost:8000/api/v1/accounts?page=0&size=20`

### Filtros opcionales

- `status`: `ACTIVA`, `INACTIVA`, `BLOQUEADA`, `SUSPENDIDA`, `CERRADA`
- `subtypeCode`: `AHO_STD`, `COR_STD`, `COR_NOM`, etc.
- `branchCode`: código de sucursal, por ejemplo `001`
- `accountPurpose`: `GENERAL`, `OPERATIVA`, `NOMINA`, `IMPUESTOS`, `PAGOS_MASIVOS`
- `search`: número de cuenta, identificación, nombre del titular o alias operativo
- `page`: página, inicia en `0`
- `size`: tamaño de página, máximo recomendado `100`

### Respuesta

```json
{
  "total": 1500,
  "page": 0,
  "size": 20,
  "totalPages": 75,
  "accounts": [
    {
      "accountUuid": "uuid",
      "accountNumber": "0010515383395",
      "customerUuid": "uuid-cliente",
      "identification": "1755555555",
      "holderName": "María Fernanda Vargas",
      "branchCode": "001",
      "subtypeCode": "AHO_STD",
      "status": "ACTIVA",
      "accountingBalance": 1000.00,
      "availableBalance": 1000.00,
      "withheldAmount": 0.00,
      "favoritePaymentAccount": true,
      "massPaymentMainAccount": false,
      "accountPurpose": "GENERAL",
      "operationalAlias": "Cuenta principal"
    }
  ]
}
```

## Buscar cuentas por identificación del cliente

`GET /api/v1/accounts/by-customer-identification/{identification}`

Ejemplo:

`GET /api/v1/accounts/by-customer-identification/1755555555`

Devuelve todas las cuentas asociadas a la cédula o RUC. Útil para atención al cliente, búsqueda rápida y pantallas administrativas.

## Endpoint de compatibilidad

`GET /api/v1/accounts/all?status=ACTIVA`

Se deja solo por compatibilidad con intentos previos del frontend. Para nuevas pantallas usar `GET /api/v1/accounts` porque tiene paginación y filtros.

## Reglas frontend

- En backoffice se puede listar y filtrar cuentas.
- En banca web cliente no usar listado global; usar `GET /api/v1/accounts/by-customer/{customerUuid}?includeBalance=true`.
- Siempre enviar token Bearer.
- Los estados se manejan en español según dominio: `ACTIVA`, `BLOQUEADA`, etc.
- Ante errores `ACCOUNT_INVALID_STATUS` o `ACCOUNT_INVALID_PURPOSE`, revisar que el select del frontend mande el enum correcto.
