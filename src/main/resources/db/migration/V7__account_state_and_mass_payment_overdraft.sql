-- R9-E: reglas de estado por canal y liquidación posterior de comisión con sobregiro controlado.
-- La comisión deja de consumir la reserva del lote y se liquida directamente contra la cuenta matriz.
-- Se conserva el monto cotizado y se registra explícitamente cuándo la comisión final quedó liquidada.

DELIMITER $$

DROP PROCEDURE IF EXISTS align_r9e_account_schema$$
CREATE PROCEDURE align_r9e_account_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'RESERVA_PAGO_MASIVO'
           AND COLUMN_NAME = 'COMISION_LIQUIDADA'
    ) THEN
        ALTER TABLE RESERVA_PAGO_MASIVO
            ADD COLUMN COMISION_LIQUIDADA TINYINT(1) NOT NULL DEFAULT 0
            AFTER MONTO_COMISION_COBRADO;
    END IF;

    UPDATE RESERVA_PAGO_MASIVO
       SET COMISION_LIQUIDADA = CASE
           WHEN MONTO_COMISION = 0
             OR MONTO_COMISION_COBRADO > 0
             OR ESTADO IN ('LIBERADA', 'CONSUMIDA_TOTAL')
           THEN 1 ELSE 0 END;

    IF EXISTS (
        SELECT 1
          FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'RESERVA_PAGO_MASIVO'
           AND CONSTRAINT_NAME = 'CK_RESERVA_PM_DISTRIBUCION'
    ) THEN
        ALTER TABLE RESERVA_PAGO_MASIVO
            DROP CHECK CK_RESERVA_PM_DISTRIBUCION;
    END IF;

    ALTER TABLE RESERVA_PAGO_MASIVO
        ADD CONSTRAINT CK_RESERVA_PM_DISTRIBUCION
        CHECK (MONTO_CONSUMIDO_ONUS + MONTO_CONSUMIDO_OFFUS
               + MONTO_LIBERADO <= MONTO_RESERVADO);

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'RESERVA_PAGO_MASIVO'
           AND CONSTRAINT_NAME = 'CK_RESERVA_PM_COMISION_LIQUIDADA'
    ) THEN
        ALTER TABLE RESERVA_PAGO_MASIVO
            ADD CONSTRAINT CK_RESERVA_PM_COMISION_LIQUIDADA
            CHECK (COMISION_LIQUIDADA IN (0, 1));
    END IF;
END$$

CALL align_r9e_account_schema()$$
DROP PROCEDURE align_r9e_account_schema$$

DELIMITER ;
