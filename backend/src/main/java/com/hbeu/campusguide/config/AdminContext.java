package com.hbeu.campusguide.config;

import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Reads the principal attached by AdminAuthInterceptor.
 */
public final class AdminContext {
    public static final String ATTRIBUTE = "adminPrincipal";

    private AdminContext() {
    }

    public static AdminPrincipal current() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        HttpServletRequest request = attributes.getRequest();
        Object principal = request.getAttribute(ATTRIBUTE);
        if (!(principal instanceof AdminPrincipal adminPrincipal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return adminPrincipal;
    }
}
