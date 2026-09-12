import { provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';

import { authInterceptor } from './core/interceptors/auth.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(
      withFetch(),
      withInterceptors([authInterceptor]),
      // Spring Security's cookie and header names (config/SecurityConfig). Angular adds
      // the header to every same-origin write by itself.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    provideBrowserGlobalErrorListeners(),
    // One screen fades into the next (styles.css: ::view-transition-*). The first paint is
    // not a transition -- there is nothing to fade from -- and reduced motion turns it off.
    provideRouter(routes, withComponentInputBinding(), withViewTransitions({ skipInitialTransition: true }))
  ]
};
