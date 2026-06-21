-- R9-I.1: alineación contractual Core-Switch para integración On-Us E2E.
-- Se agregan campos explícitos para comisión subtotal + IVA + total debitado.
-- La migración es aditiva y tolera bases persistidas desde R9-H.

DELIMITER $$

DROP PROCEDURE IF EXISTS align_r9i_switch_core_contracts$$
CREATE PROCEDURE align_r9i_switch_core_contracts()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'RESERVA_PAGO_MASIVO'
           AND COLUMN_NAME = 'MONTO_COMISION_IVA_COBRADO'
    ) THEN
        ALTER TABLE RESERVA_PAGO_MASIVO
            ADD COLUMN MONTO_COMISION_IVA_COBRADO DECIMAL(19,2) NOT NULL DEFAULT 0.00
            AFTER MONTO_COMISION_COBRADO;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'RESERVA_PAGO_MASIVO'
           AND COLUMN_NAME = 'MONTO_COMISION_TOTAL_COBRADO'
    ) THEN
        ALTER TABLE RESERVA_PAGO_MASIVO
            ADD COLUMN MONTO_COMISION_TOTAL_COBRADO DECIMAL(19,2) NOT NULL DEFAULT 0.00
            AFTER MONTO_COMISION_IVA_COBRADO;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'RESERVA_PAGO_MASIVO'
           AND COLUMN_NAME = 'UUID_TRANSACCION_COMISION'
    ) THEN
        ALTER TABLE RESERVA_PAGO_MASIVO
            ADD COLUMN UUID_TRANSACCION_COMISION CHAR(36) NULL
            AFTER MONTO_COMISION_TOTAL_COBRADO;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'RESERVA_PAGO_MASIVO'
           AND COLUMN_NAME = 'ASIENTO_COMISION_UUID'
    ) THEN
        ALTER TABLE RESERVA_PAGO_MASIVO
            ADD COLUMN ASIENTO_COMISION_UUID CHAR(36) NULL
            AFTER UUID_TRANSACCION_COMISION;
    END IF;

    -- Compatibilidad con reservas ya existentes: el valor histórico cobrado se conserva
    -- como total conocido cuando aún no existía desglose subtotal/IVA explícito.
    UPDATE RESERVA_PAGO_MASIVO
       SET MONTO_COMISION_TOTAL_COBRADO = MONTO_COMISION_COBRADO
     WHERE MONTO_COMISION_TOTAL_COBRADO = 0.00
       AND MONTO_COMISION_COBRADO > 0.00;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'RESERVA_PAGO_MASIVO'
           AND CONSTRAINT_NAME = 'CK_RESERVA_PM_COMISION_DESGLOSE_R9I'
    ) THEN
        ALTER TABLE RESERVA_PAGO_MASIVO
            ADD CONSTRAINT CK_RESERVA_PM_COMISION_DESGLOSE_R9I
            CHECK (MONTO_COMISION_IVA_COBRADO >= 0
                   AND MONTO_COMISION_TOTAL_COBRADO >= 0
                   AND MONTO_COMISION_TOTAL_COBRADO >= MONTO_COMISION_COBRADO);
    END IF;
END$$

CALL align_r9i_switch_core_contracts()$$
DROP PROCEDURE align_r9i_switch_core_contracts$$

DELIMITER ;
