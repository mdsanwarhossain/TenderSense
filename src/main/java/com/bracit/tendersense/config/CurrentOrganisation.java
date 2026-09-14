package com.bracit.tendersense.config;

import java.lang.annotation.*;

/**
 * Injects the company the signed-in account belongs to (see OrganisationArgumentResolver).
 * Only company accounts reach these endpoints; SecurityConfig refuses the rest.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentOrganisation {
}
