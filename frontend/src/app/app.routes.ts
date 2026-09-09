import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
    title: 'Sign in · TenderSense',
  },
  {
    path: 'signup',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/signup').then((m) => m.Signup),
    title: 'Create an account · TenderSense',
  },

  // Everything below needs a signed-in company.
  { path: '', pathMatch: 'full', redirectTo: 'shortlist' },
  {
    path: 'shortlist',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shortlist/shortlist').then((m) => m.Shortlist),
    title: 'Morning shortlist · TenderSense',
  },
  {
    path: 'tenders/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./features/tender-detail/tender-detail').then((m) => m.TenderDetail),
    title: 'Tender · TenderSense',
  },
  {
    path: 'benchmark',
    canActivate: [authGuard],
    loadComponent: () => import('./features/benchmark/benchmark').then((m) => m.Benchmark),
    title: 'Benchmark · TenderSense',
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
    title: 'Capability profile · TenderSense',
  },
  {
    path: 'pipeline',
    canActivate: [authGuard],
    loadComponent: () => import('./features/pipeline/pipeline').then((m) => m.Pipeline),
    title: 'Pipeline · TenderSense',
  },
  { path: '**', redirectTo: 'shortlist' },
];
