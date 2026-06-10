package com.banquito.core.account.domain.repository;

import com.banquito.core.account.domain.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
import com.banquito.core.account.domain.enums.*;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    Optional<Cuenta> findByNumeroCuenta(String numeroCuenta);
    Optional<Cuenta> findByUuidCuenta(String uuidCuenta);
    List<Cuenta> findByUuidClienteOrderByFechaAperturaDesc(String uuidCliente);
    List<Cuenta> findByUuidClienteAndEstadoOrderByFechaAperturaDesc(String uuidCliente, EstadoCuentaEnum estado);
    List<Cuenta> findByUuidClienteAndPropositoCuentaOrderByFechaAperturaDesc(String uuidCliente, PropositoCuentaEnum propositoCuenta);
    Optional<Cuenta> findByUuidClienteAndEsCuentaFavoritaPagos(String uuidCliente, Boolean esCuentaFavoritaPagos);

    List<Cuenta> findByIdentificacionTitularOrderByFechaAperturaDesc(String identificacionTitular);

    List<Cuenta> findAllByOrderByFechaAperturaDesc();

    List<Cuenta> findByEstadoOrderByFechaAperturaDesc(EstadoCuentaEnum estado);

    @Query(value = """
            SELECT c
            FROM Cuenta c
            WHERE (:estado IS NULL OR c.estado = :estado)
              AND (:subtypeCode IS NULL OR c.codigoSubtipoCuenta = :subtypeCode)
              AND (:branchCode IS NULL OR c.codigoSucursal = :branchCode)
              AND (:propositoCuenta IS NULL OR c.propositoCuenta = :propositoCuenta)
              AND (
                    :search IS NULL OR
                    LOWER(c.numeroCuenta) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(c.identificacionTitular) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(c.nombreTitularReferencia) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(COALESCE(c.aliasOperativo, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY c.fechaApertura DESC
            """,
            countQuery = """
            SELECT COUNT(c)
            FROM Cuenta c
            WHERE (:estado IS NULL OR c.estado = :estado)
              AND (:subtypeCode IS NULL OR c.codigoSubtipoCuenta = :subtypeCode)
              AND (:branchCode IS NULL OR c.codigoSucursal = :branchCode)
              AND (:propositoCuenta IS NULL OR c.propositoCuenta = :propositoCuenta)
              AND (
                    :search IS NULL OR
                    LOWER(c.numeroCuenta) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(c.identificacionTitular) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(c.nombreTitularReferencia) LIKE LOWER(CONCAT('%', :search, '%')) OR
                    LOWER(COALESCE(c.aliasOperativo, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  )
            """)
    Page<Cuenta> searchBackofficeAccounts(
            @Param("estado") EstadoCuentaEnum estado,
            @Param("subtypeCode") String subtypeCode,
            @Param("branchCode") String branchCode,
            @Param("propositoCuenta") PropositoCuentaEnum propositoCuenta,
            @Param("search") String search,
            Pageable pageable);

}
