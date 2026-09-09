import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'shortlist' },
  {
    path: 'shortlist',
    loadComponent: () => import('./features/shortlist/shortlist').then((m) => m.Shortlist),
    title: 'Morning shortlist · TenderSense',
  },
  {
    path: 'tenders/:id',
    loadComponent: () => import('./features/tender-detail/tender-detail').then((m) => m.TenderDetail),
    title: 'Tender · TenderSense',
  },
  {
    path: 'benchmark',
    loadComponent: () => import('./features/benchmark/benchmark').then((m) => m.Benchmark),
    title: 'Benchmark · TenderSense',
  },
  {
    path: 'profile',
    loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
    title: 'Capability profile · TenderSense',
  },
  {
    path: 'pipeline',
    loadComponent: () => import('./features/pipeline/pipeline').then((m) => m.Pipeline),
    title: 'Pipeline · TenderSense',
  },
  { path: '**', redirectTo: 'shortlist' },
];
