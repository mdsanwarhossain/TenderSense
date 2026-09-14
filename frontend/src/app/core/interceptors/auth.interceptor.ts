import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Sends an expired session back to the sign-in page, and a screen this account may not
 * use back to its own home.
 *
 * The route guards only run on navigation. A session that expires (or an account an
 * admin switches off) under a page which is already open surfaces as a failed request,
 * not a failed navigation — without this the screen would just show an error and keep
 * pretending to be signed in.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      // `/me` answers 401 by design when signed out — that is how the guard learns
      // the answer, and treating it as an expiry would bounce the sign-in page.
      const probing = req.url.startsWith('/api/auth/');
      if (error.status === 401 && !probing) {
        auth.clear();
        void router.navigate(['/login']);
      }
      // A 403 for a missing security token is a page to reload, not a place to leave;
      // the screen shows the API's message for that one.
      const detail = (error.error as { detail?: string } | null)?.detail ?? '';
      if (error.status === 403 && !probing && !detail.includes('security token')) {
        void router.navigateByUrl(auth.home());
      }
      return throwError(() => error);
    }),
  );
};
