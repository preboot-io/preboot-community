package io.preboot.auth.core;

import io.preboot.auth.api.UserAccountManagementApi;
import io.preboot.auth.api.dto.*;
import io.preboot.auth.core.usecase.*;
import io.preboot.core.validation.BeanValidator;
import io.preboot.query.SearchParams;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class UserAccountManagementApiImpl implements UserAccountManagementApi {
    private final GetUserAccountUseCase getUserAccountUseCase;
    private final CreateInactiveUserAccountUseCase createUserAccountUseCase;
    private final ManageUserAccountRolesUseCase manageUserAccountRolesUseCase;
    private final UpdatePasswordUseCase updatePasswordUseCase;
    private final RemoveUserAccountUseCase removeUserAccountUseCase;
    private final CreateTenantAndInactiveUserAccountUseCase createTenantAndInactiveUserAccountUseCase;
    private final ResendActivationLinkUseCase resendActivationLinkUseCase;

    @Override
    public UserAccountInfo getUserAccount(final UUID userAccountId, final UUID tenantId) {
        return getUserAccountUseCase.execute(userAccountId, tenantId);
    }

    @Override
    public UserAccountInfo createUserAccountForTenant(final CreateInactiveUserAccountRequest request) {
        return createUserAccountUseCase.execute(request);
    }

    @Override
    public UserAccountInfo createTenantAndUserAccount(final CreateTenantAndInactiveUserAccountRequest request) {
        return createTenantAndInactiveUserAccountUseCase.execute(request);
    }

    @Override
    public void resendActivationLink(ResentActivationLinkCommand command) {
        resendActivationLinkUseCase.execute(command);
    }

    @Override
    public void updatePassword(final UpdatePasswordCommand command) {
        BeanValidator.validate(command);
        updatePasswordUseCase.execute(command);
    }

    @Override
    public UserAccountInfo addRole(final UUID userId, final UUID tenantId, final String roleName) {
        return manageUserAccountRolesUseCase.addRole(userId, tenantId, roleName);
    }

    @Override
    public UserAccountInfo removeRole(final UUID userId, final UUID tenantId, final String roleName) {
        return manageUserAccountRolesUseCase.removeRole(userId, tenantId, roleName);
    }

    @Override
    public Page<UserAccountInfo> getUserAccountsInfo(final SearchParams searchParams, final UUID tenantId) {
        return getUserAccountUseCase.getUserAccountsInfo(searchParams, tenantId);
    }

    @Override
    public Page<UserAccountInfo> getAllUserAccountsInfo(SearchParams params) {
        return getUserAccountUseCase.getAllUserAccountsInfo(params);
    }

    @Override
    public void removeUser(final UUID userId, final UUID tenantId) {
        removeUserAccountUseCase.execute(userId, tenantId);
    }
}
