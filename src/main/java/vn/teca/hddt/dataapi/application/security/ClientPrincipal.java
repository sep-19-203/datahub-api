package vn.teca.hddt.dataapi.application.security;

import java.util.Objects;
import java.util.Set;

import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.domain.common.Jdk8Compat;

/** Client đã xác thực. {@code allMst=false} ⇒ chỉ khai thác các MST trong {@code allowedMsts}. */
public final class ClientPrincipal {
    private final String clientId;
    private final Set<ApiPermission> permissions;
    private final boolean allMst;
    private final Set<String> allowedMsts;

    public ClientPrincipal(String clientId, Set<ApiPermission> permissions, boolean allMst, Set<String> allowedMsts) {
        permissions = Jdk8Compat.setCopyOf(permissions);
        allowedMsts = allowedMsts == null ? Jdk8Compat.setOf() : Jdk8Compat.setCopyOf(allowedMsts);
        this.clientId = clientId;
        this.permissions = permissions;
        this.allMst = allMst;
        this.allowedMsts = allowedMsts;
    }

    public String clientId() {
        return clientId;
    }

    public Set<ApiPermission> permissions() {
        return permissions;
    }

    public boolean allMst() {
        return allMst;
    }

    public Set<String> allowedMsts() {
        return allowedMsts;
    }

    public boolean has(ApiPermission p) {
        return permissions.contains(p);
    }

    public void requireMst(String mst) {
        if (!allMst && !allowedMsts.contains(mst)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Client không được phép khai thác dữ liệu của MST này");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientPrincipal)) {
            return false;
        }
        ClientPrincipal other = (ClientPrincipal) o;
        return Objects.equals(clientId, other.clientId)
                && Objects.equals(permissions, other.permissions)
                && allMst == other.allMst
                && Objects.equals(allowedMsts, other.allowedMsts);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(clientId);
        result = 31 * result + Objects.hashCode(permissions);
        result = 31 * result + Boolean.hashCode(allMst);
        result = 31 * result + Objects.hashCode(allowedMsts);
        return result;
    }

    @Override
    public String toString() {
        return "ClientPrincipal[clientId=" + clientId
                + ", permissions=" + permissions
                + ", allMst=" + allMst
                + ", allowedMsts=" + allowedMsts + "]";
    }
}
