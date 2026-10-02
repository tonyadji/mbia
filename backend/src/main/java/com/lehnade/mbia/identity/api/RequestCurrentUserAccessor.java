package com.lehnade.mbia.identity.api;

import com.lehnade.mbia.identity.application.CurrentUser;
import com.lehnade.mbia.identity.application.CurrentUserAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Reads the User stored on the request by {@link CurrentUserInterceptor}. */
@Component
class RequestCurrentUserAccessor implements CurrentUserAccessor {

    private static final String ATTRIBUTE = RequestCurrentUserAccessor.class.getName();

    static void store(HttpServletRequest request, CurrentUser user) {
        request.setAttribute(ATTRIBUTE, user);
    }

    @Override
    public CurrentUser currentUser() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        Object user = request == null ? null : request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (user instanceof CurrentUser currentUser) {
            return currentUser;
        }
        throw new IllegalStateException("No authenticated API request");
    }
}
