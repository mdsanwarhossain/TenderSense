import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Sends an expired session back to the sign-in page.
 *
 * The route guard only runs on navigation. A session that expires under a page which
 * is already open surfaces as a failed request, not a failed navigation — without this
 * the screen would just show an error and keep pretending to be signed in.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      const is401 = error instanceof HttpErrorResponse && error.status === 401;
      // `/me` answers 401 by design when signed out — that is how the guard learns
      // the answer, and treating it as an expiry would bounce the sign-in page.
      const probing = req.url.startsWith('/api/auth/');
      if (is401 && !probing) {
        auth.clear();
        void router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
