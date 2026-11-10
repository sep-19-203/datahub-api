package vn.teca.hddt.dataapi.adapter.web;

import javax.servlet.http.HttpServletRequest;
import vn.teca.hddt.dataapi.application.error.ApiException;
import vn.teca.hddt.dataapi.application.error.ErrorCode;
import vn.teca.hddt.dataapi.application.security.ClientPrincipal;

/** Thuộc tính request do filter xác thực gắn vào. */
public final class RequestContext {
    public static final String PRINCIPAL = RequestContext.class.getName() + ".principal";
    public static final String TRACE_ID = RequestContext.class.getName() + ".traceId";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    private RequestContext() {
    }

    public static ClientPrincipal principal(HttpServletRequest req) {
        Object p = req.getAttribute(PRINCIPAL);
        if (!(p instanceof ClientPrincipal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return (ClientPrincipal) p;
    }

    public static String traceId(HttpServletRequest req) {
        Object t = req.getAttribute(TRACE_ID);
        return t == null ? null : t.toString();
    }
}
