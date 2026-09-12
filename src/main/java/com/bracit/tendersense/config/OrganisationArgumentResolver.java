package com.bracit.tendersense.config;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.exception.UnauthenticatedException;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves which company a request is acting for, and injects it wherever a controller
 * declares a {@link CurrentOrganisation} parameter.
 *
 * <p>The source is the signed-in account in Spring Security's context. Every controller
 * signature stayed as it was when this moved off the hand-rolled session attribute.
 */
@Component
@RequiredArgsConstructor
public class OrganisationArgumentResolver implements HandlerMethodArgumentResolver {

    private final OrganisationRepository organisationRepository;

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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AccountPrincipal principal)
                || principal.getOrganisationId() == null) {
            // SecurityConfig already refuses company endpoints to anyone without a company;
            // this only catches an endpoint mapped outside those rules.
            throw new UnauthenticatedException("Not signed in to a company");
        }
        return organisationRepository.findById(principal.getOrganisationId())
                .filter(Organisation::isActive)
                .orElseThrow(() -> new UnauthenticatedException("Not signed in"));
    }
}
