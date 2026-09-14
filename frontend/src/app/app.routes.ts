import { Routes } from '@angular/router';
import { adminGuard, companyGuard, guestGuard, homeGuard } from './core/guards/auth.guard';

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

  // `/` goes to the account's own home: the company dashboard, or the admin panel.
  { path: '', pathMatch: 'full', canActivate: [homeGuard], children: [] },

  // ---- company screens ----
  {
    path: 'dashboard',
    canActivate: [companyGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
    title: 'Dashboard · TenderSense',
  },
  {
    path: 'shortlist',
    canActivate: [companyGuard],
    loadComponent: () => import('./features/shortlist/shortlist').then((m) => m.Shortlist),
    title: 'Tender list · TenderSense',
  },
  {
    path: 'tenders/:id',
    canActivate: [companyGuard],
    loadComponent: () => import('./features/tender-detail/tender-detail').then((m) => m.TenderDetail),
    title: 'Tender · TenderSense',
  },
  {
    path: 'benchmark',
    canActivate: [companyGuard],
    loadComponent: () => import('./features/benchmark/benchmark').then((m) => m.Benchmark),
    title: 'Benchmark · TenderSense',
  },
  {
    path: 'profile',
    canActivate: [companyGuard],
    loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
    title: 'Profile · TenderSense',
  },

  // ---- admin panel: TenderSense staff ----
  {
    path: 'admin',
    canActivate: [adminGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./features/admin/admin-dashboard').then((m) => m.AdminDashboardPage),
        title: 'Admin · TenderSense',
      },
      {
        path: 'tenders',
        loadComponent: () => import('./features/admin/tenders').then((m) => m.AdminTenders),
        title: 'Tenders · TenderSense',
      },
      {
        path: 'companies',
        loadComponent: () => import('./features/admin/companies').then((m) => m.AdminCompanies),
        title: 'Companies · TenderSense',
      },
      {
        path: 'users',
        loadComponent: () => import('./features/admin/users').then((m) => m.AdminUsers),
        title: 'Users · TenderSense',
      },
      {
        path: 'pipeline',
        loadComponent: () => import('./features/pipeline/pipeline').then((m) => m.Pipeline),
        title: 'Pipeline · TenderSense',
      },
      {
        path: 'scheduler',
        loadComponent: () => import('./features/admin/scheduler').then((m) => m.AdminScheduler),
        title: 'Scheduler · TenderSense',
      },
    ],
  },
  // The old address, for bookmarks.
  { path: 'pipeline', redirectTo: 'admin/pipeline' },

  { path: '**', canActivate: [homeGuard], children: [] },
];
