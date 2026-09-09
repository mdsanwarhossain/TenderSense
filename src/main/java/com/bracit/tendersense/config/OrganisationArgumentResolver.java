package com.bracit.tendersense.config;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Turns the {@code X-Org-Id} header into an {@link Organisation}.
 *
 * <p>A header rather than a {@code /api/orgs/{id}/...} path prefix, so multi-tenancy did
 * not require rewriting every existing URL. An absent header resolves to the first active
 * organisation, which keeps single-tenant callers working unchanged; an unknown or
 * inactive id is a 404 rather than a silent fallback, because quietly serving a different
 * company's shortlist would be worse than an error.
 */
@Component
@RequiredArgsConstructor
public class OrganisationArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER = "X-Org-Id";

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
        String header = webRequest.getHeader(HEADER);

        if (header != null && !header.isBlank()) {
            Long id;
            try {
                id = Long.parseLong(header.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(HEADER + " must be a numeric id, got: " + header);
            }
            return organisationRepository.findById(id)
                    .filter(Organisation::isActive)
                    .orElseThrow(() -> new NotFoundException(
                            "no active organisation with id " + id));
        }

        return organisationRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException(
                        "no active organisation is configured"));
    }
}
