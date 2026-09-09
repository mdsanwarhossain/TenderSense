package com.bracit.tendersense.config;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.exception.UnauthenticatedException;
import com.bracit.tendersense.repository.OrganisationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves which company a request is acting for, and injects it wherever a controller
 * declares a {@link CurrentOrganisation} parameter.
 *
 * <p>The source is the <strong>session</strong>. This is the one seam that changed when
 * sign-in landed: every controller signature, every {@code @CurrentOrganisation} parameter
 * and every URL stayed exactly as it was, which is why the header was originally chosen
 * over a {@code /api/orgs/{id}/...} path prefix.
 */
@Component
@RequiredArgsConstructor
public class OrganisationArgumentResolver implements HandlerMethodArgumentResolver {

    /** Session attribute holding the signed-in organisation id. */
    public static final String SESSION_KEY = "tendersense.orgId";

    public static final String HEADER = "X-Org-Id";

    private final OrganisationRepository organisationRepository;

    /**
     * Test seam. When true, a request may name its company with {@link #HEADER} instead of
     * signing in -- which is authentication bypass, so it defaults to false and is switched
     * on only by test properties. {@code TenantIsolationIT} drives two companies through one
     * MockMvc and would otherwise have to perform logins to prove tenant isolation.
     */
    @Value("${tendersense.auth.allow-header:false}")
    private boolean allowHeader;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentOrganisation.class)
                && Organisation.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        HttpServletRequest http = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = http == null ? null : http.getSession(false);
        Long orgId = session == null ? null : (Long) session.getAttribute(SESSION_KEY);

        if (orgId != null) {
            return organisationRepository.findById(orgId)
                    .filter(Organisation::isActive)
                    .orElseThrow(() -> {
                        // The company was deactivated or the database rebuilt underneath a
                        // live session. Ending it is better than serving a stale identity.
                        session.invalidate();
                        return new UnauthenticatedException("Not signed in");
                    });
        }

        if (allowHeader) {
            String header = webRequest.getHeader(HEADER);
            if (header != null && !header.isBlank()) {
                return fromHeader(header);
            }
            return organisationRepository.findFirstByActiveTrueOrderByIdAsc()
                    .orElseThrow(() -> new NotFoundException("no active organisation is configured"));
        }

        throw new UnauthenticatedException("Not signed in");
    }

    private Organisation fromHeader(String header) {
        Long id;
        try {
            id = Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(HEADER + " must be a numeric id, got: " + header);
        }
        return organisationRepository.findById(id)
                .filter(Organisation::isActive)
                .orElseThrow(() -> new NotFoundException("no active organisation with id " + id));
    }
}
