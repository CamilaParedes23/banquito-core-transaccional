-- Fase 1.2: trazabilidad e integridad de reversos financieros.
-- Las comprobaciones hacen la migración recuperable si MySQL confirmó parcialmente un DDL previo.

DELIMITER $$

DROP PROCEDURE IF EXISTS align_account_reversal_schema$$
CREATE PROCEDURE align_account_reversal_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'TRANSACCION_CUENTA'
           AND COLUMN_NAME = 'MOTIVO_REVERSO'
    ) THEN
        ALTER TABLE TRANSACCION_CUENTA
            ADD COLUMN MOTIVO_REVERSO VARCHAR(300) NULL AFTER UUID_DOCUMENTO_COMPROBANTE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'TRANSACCION_CUENTA'
           AND COLUMN_NAME = 'UUID_USUARIO_REVERSO'
    ) THEN
        ALTER TABLE TRANSACCION_CUENTA
            ADD COLUMN UUID_USUARIO_REVERSO CHAR(36) NULL AFTER MOTIVO_REVERSO;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'TRANSACCION_CUENTA'
           AND INDEX_NAME = 'UK_TX_UNICO_REVERSO'
    ) THEN
        CREATE UNIQUE INDEX UK_TX_UNICO_REVERSO
            ON TRANSACCION_CUENTA (TRANSACCION_REVERSADA_ID);
    END IF;
END$$

CALL align_account_reversal_schema()$$
DROP PROCEDURE align_account_reversal_schema$$

DELIMITER ;
