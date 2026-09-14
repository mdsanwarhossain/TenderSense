import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Route guards by account type. The API enforces the same rules (SecurityConfig); these
 * only keep people off screens that would fail, and send them somewhere useful instead.
 */

/** Remember where they were going, so signing in resumes it instead of starting over. */
function toLogin(router: Router, url: string): UrlTree {
  return router.createUrlTree(['/login'], { queryParams: { next: url } });
}

/** Company screens: a signed-in account that belongs to a company. */
export const companyGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = await auth.ensureLoaded();
  if (!user) return toLogin(router, state.url);
  return user.organisation ? true : router.createUrlTree([auth.home(user)]);
};

/** The admin panel: TenderSense staff only. */
export const adminGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = await auth.ensureLoaded();
  if (!user) return toLogin(router, state.url);
  return user.role === 'ADMIN' ? true : router.createUrlTree([auth.home(user)]);
};

/** Keeps a signed-in account off the sign-in and sign-up pages. */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = await auth.ensureLoaded();
  return user ? router.createUrlTree([auth.home(user)]) : true;
};

/** For `/` and unknown URLs: always redirects, to the account's own home or to sign-in. */
export const homeGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return router.createUrlTree([auth.home(await auth.ensureLoaded())]);
};
