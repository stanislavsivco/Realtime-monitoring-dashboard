import { Routes } from '@angular/router';

export const routes: Routes = [
    { path: 'login', loadComponent: () => import('./auth/login/login').then(m => m.Login) },
    { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard').then(m => m.Dashboard) },
    { path: 'devices/:id', loadComponent: () => import('./device-detail/device-detail').then(m => m.DeviceDetail) },
    { path: 'alerts', loadComponent: () => import('./alerts/alerts').then(m => m.Alerts) },
    { path: 'admin', loadComponent: () => import('./admin/admin').then(m => m.Admin) },
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];