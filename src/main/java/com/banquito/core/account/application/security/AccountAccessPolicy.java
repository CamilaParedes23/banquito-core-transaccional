package com.banquito.core.account.application.security;

import com.banquito.core.account.api.dto.internal.AuthenticatedActor;
import com.banquito.core.account.domain.model.Cuenta;
import com.banquito.core.account.domain.model.ReservaPagoMasivo;
import com.banquito.core.account.domain.repository.CuentaRepository;
import com.banquito.core.account.domain.repository.ReservaPagoMasivoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("accountAccessPolicy")
public class AccountAccessPolicy {
    private static final String ROLE_ADMIN = "ADMIN_SEGURIDAD";
    private static final String ROLE_TELLER = "CAJERO";
    private static final String ROLE_ACCOUNTING = "OPERADOR_CONTABLE";
    private static final String ROLE_CLIENT_PERSON = "CLIENTE_PERSONA";
    private static final String ROLE_CLIENT_COMPANY = "CLIENTE_EMPRESA";
    private static final String ROLE_SERVICE = "SERVICE_CLIENT";

    private static final String SCOPE_BALANCE_READ = "core.account.balance.read";
    private static final String SCOPE_TRANSFER_P2P = "core.account.transfer.p2p";
    private static final String SCOPE_RESERVE_CREATE = "core.reserve.create";
    private static final String SCOPE_RESERVE_CONSUME = "core.reserve.consume";
    private static final String SCOPE_RESERVE_RELEASE = "core.reserve.release";

    private final CuentaRepository cuentaRepository;
    private final ReservaPagoMasivoRepository reservaRepository;

    public AccountAccessPolicy(CuentaRepository cuentaRepository, ReservaPagoMasivoRepository reservaRepository) {
        this.cuentaRepository = cuentaRepository;
        this.reservaRepository = reservaRepository;
    }

    public boolean canBackoffice(Authentication authentication) {
        AuthenticatedActor actor = actor(authentication);
        return actor != null && hasAnyRole(actor, ROLE_ADMIN, ROLE_TELLER, ROLE_ACCOUNTING);
    }

    public boolean canReadCustomer(Authentication authentication, String customerUuid) {
        AuthenticatedActor actor = actor(authentication);
        if (actor == null || isBlank(customerUuid)) return false;
        if (canBackoffice(authentication)) return true;
        return hasScope(actor, SCOPE_BALANCE_READ) && ownsCustomer(actor, customerUuid);
    }

    public boolean canReadIdentification(Authentication authentication, String identification) {
        AuthenticatedActor actor = actor(authentication);
        if (actor == null || isBlank(identification)) return false;
        if (canBackoffice(authentication)) return true;
        // Por seguridad no se autoriza lectura por identificación para clientes finales,
        // porque el JWT no debe confiar en identificadores enviados desde el frontend.
        return false;
    }

    public boolean canReadAccount(Authentication authentication, String accountNumber) {
        AuthenticatedActor actor = actor(authentication);
        if (actor == null || isBlank(accountNumber)) return false;
        if (canBackoffice(authentication)) return true;
        if (!hasScope(actor, SCOPE_BALANCE_READ)) return false;
        return cuentaRepository.findByNumeroCuenta(accountNumber.trim())
                .map(cuenta -> ownsAccount(actor, cuenta))
                .orElse(false);
    }

    public boolean canValidateP2pBeneficiary(Authentication authentication) {
        AuthenticatedActor actor = actor(authentication);
        if (actor == null) return false;
        if (canBackoffice(authentication)) return true;
        return hasRole(actor, ROLE_CLIENT_PERSON) && hasScope(actor, SCOPE_TRANSFER_P2P);
    }

    public boolean canTransferP2p(Authentication authentication, String sourceAccountNumber) {
        AuthenticatedActor actor = actor(authentication);
        if (actor == null || isBlank(sourceAccountNumber)) return false;
        if (canBackoffice(authentication)) return true;
        if (!hasRole(actor, ROLE_CLIENT_PERSON) || !hasScope(actor, SCOPE_TRANSFER_P2P)) return false;
        return cuentaRepository.findByNumeroCuenta(sourceAccountNumber.trim())
                .map(cuenta -> ownsAccount(actor, cuenta))
                .orElse(false);
    }

    public boolean canCreateReservation(Authentication authentication, String companyCustomerUuid, String mainAccountNumber) {
        AuthenticatedActor actor = actor(authentication);
        if (actor == null || isBlank(companyCustomerUuid) || isBlank(mainAccountNumber)) return false;
        if (canBackoffice(authentication)) return true;
        if (hasRole(actor, ROLE_SERVICE) && hasScope(actor, SCOPE_RESERVE_CREATE)) return true;
        if (!hasRole(actor, ROLE_CLIENT_COMPANY) || !hasScope(actor, SCOPE_RESERVE_CREATE) || !ownsCustomer(actor, companyCustomerUuid)) return false;
        return cuentaRepository.findByNumeroCuenta(mainAccountNumber.trim())
                .map(cuenta -> companyCustomerUuid.trim().equals(cuenta.getUuidCliente()))
                .orElse(false);
    }

    public boolean canConsumeReservation(Authentication authentication, String reservationUuid) {
        return canAccessReservation(authentication, reservationUuid, SCOPE_RESERVE_CONSUME);
    }

    public boolean canReleaseReservation(Authentication authentication, String reservationUuid) {
        return canAccessReservation(authentication, reservationUuid, SCOPE_RESERVE_RELEASE);
    }

    public boolean canManageReservation(Authentication authentication, String reservationUuid) {
        AuthenticatedActor actor = actor(authentication);
        if (actor == null || isBlank(reservationUuid)) return false;
        if (canBackoffice(authentication)) return true;
        return hasRole(actor, ROLE_SERVICE) && (hasScope(actor, SCOPE_RESERVE_CONSUME) || hasScope(actor, SCOPE_RESERVE_RELEASE));
    }

    private boolean canAccessReservation(Authentication authentication, String reservationUuid, String requiredScope) {
        AuthenticatedActor actor = actor(authentication);
        if (actor == null || isBlank(reservationUuid)) return false;
        if (canBackoffice(authentication)) return true;
        if (hasRole(actor, ROLE_SERVICE) && hasScope(actor, requiredScope)) return true;
        if (!hasRole(actor, ROLE_CLIENT_COMPANY) || !hasScope(actor, requiredScope)) return false;
        return reservaRepository.findByUuidReserva(reservationUuid.trim())
                .map(reserva -> ownsReservation(actor, reserva))
                .orElse(false);
    }

    private boolean ownsReservation(AuthenticatedActor actor, ReservaPagoMasivo reserva) {
        return ownsCustomer(actor, reserva.getUuidClienteEmpresa());
    }

    private boolean ownsAccount(AuthenticatedActor actor, Cuenta cuenta) {
        return ownsCustomer(actor, cuenta.getUuidCliente());
    }

    private boolean ownsCustomer(AuthenticatedActor actor, String customerUuid) {
        return !isBlank(actor.customerUuid()) && !isBlank(customerUuid) && actor.customerUuid().trim().equals(customerUuid.trim());
    }

    private AuthenticatedActor actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) return null;
        return actor;
    }

    private boolean hasAnyRole(AuthenticatedActor actor, String... roles) {
        Set<String> userRoles = Set.copyOf(actor.roles());
        for (String role : roles) {
            if (userRoles.contains(role)) return true;
        }
        return false;
    }

    private boolean hasRole(AuthenticatedActor actor, String role) {
        return actor.roles().contains(role);
    }

    private boolean hasScope(AuthenticatedActor actor, String scope) {
        return actor.scopes().contains(scope);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
