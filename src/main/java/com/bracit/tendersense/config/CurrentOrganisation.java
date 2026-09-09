package com.bracit.tendersense.config;

import java.lang.annotation.*;

/**
 * Injects the organisation a request is acting for.
 *
 * <p>Resolved from the {@code X-Org-Id} header, falling back to the first active
 * organisation when the header is absent — so every existing URL keeps working and a
 * single-tenant deployment needs no client changes.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentOrganisation {
}
