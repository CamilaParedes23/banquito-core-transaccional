-- R9-C: expiración opcional y trazabilidad operativa de bloqueos monetarios.
-- Migración aditiva y recuperable para instalaciones existentes.

DELIMITER $$

DROP PROCEDURE IF EXISTS align_account_blocks_schema$$
CREATE PROCEDURE align_account_blocks_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'BLOQUEO_CUENTA'
           AND COLUMN_NAME = 'FECHA_EXPIRACION'
    ) THEN
        ALTER TABLE BLOQUEO_CUENTA
            ADD COLUMN FECHA_EXPIRACION DATETIME NULL AFTER FECHA_BLOQUEO;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'BLOQUEO_CUENTA'
           AND CONSTRAINT_NAME = 'CK_BLOQUEO_ESTADO'
           AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE BLOQUEO_CUENTA DROP CHECK CK_BLOQUEO_ESTADO;
    END IF;

    ALTER TABLE BLOQUEO_CUENTA
        ADD CONSTRAINT CK_BLOQUEO_ESTADO
        CHECK (ESTADO IN ('ACTIVO','LIBERADO','REVOCADO','EXPIRADO'));

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'BLOQUEO_CUENTA'
           AND INDEX_NAME = 'IDX_BLOQUEO_CUENTA_ESTADO_EXPIRACION'
    ) THEN
        CREATE INDEX IDX_BLOQUEO_CUENTA_ESTADO_EXPIRACION
            ON BLOQUEO_CUENTA (CUENTA_ID, ESTADO, FECHA_EXPIRACION);
    END IF;
END$$

CALL align_account_blocks_schema()$$
DROP PROCEDURE align_account_blocks_schema$$

DELIMITER ;
