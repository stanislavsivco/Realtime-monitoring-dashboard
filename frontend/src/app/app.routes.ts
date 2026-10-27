import { Routes } from '@angular/router';
import { authGuard } from './auth/login/auth.guard';

export const routes: Routes = [
    { path: 'login', loadComponent: () => import('./auth/login/login').then(m => m.Login) },
    { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard').then(m => m.Dashboard), canActivate: [authGuard] },
    { path: 'devices/:id', loadComponent: () => import('./device-detail/device-detail').then(m => m.DeviceDetail), canActivate: [authGuard] },
    { path: 'alerts', loadComponent: () => import('./alerts/alerts').then(m => m.Alerts), canActivate: [authGuard] },
    { path: 'admin', loadComponent: () => import('./admin/admin').then(m => m.Admin), canActivate: [authGuard] },
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },   // presmeruje na dashboard
];