import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OrgService } from '../services/org.service';

/**
 * Attaches the selected company to every API call.
 *
 * Scoping is a backend concern — every endpoint resolves this header into an
 * Organisation and filters on it — so the frontend's whole contribution to
 * multi-tenancy is this one header.
 */
export const orgInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api')) {
    return next(req);
  }
  const id = inject(OrgService).currentId();
  return next(id === null ? req : req.clone({ setHeaders: { 'X-Org-Id': String(id) } }));
};
