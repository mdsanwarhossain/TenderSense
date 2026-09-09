import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Everything except /login and /signup needs a signed-in company. */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (await auth.ensureLoaded()) {
    return true;
  }
  // Remember where they were going, so signing in resumes it instead of always
  // dumping them on the shortlist.
  return router.createUrlTree(['/login'], { queryParams: { next: state.url } });
};

/** Keeps a signed-in company off the sign-in and sign-up pages. */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return (await auth.ensureLoaded()) ? router.createUrlTree(['/shortlist']) : true;
};
