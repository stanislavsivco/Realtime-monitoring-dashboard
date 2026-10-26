import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { WebsocketService } from './websocket.service';
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
    const authService = inject(AuthService);
    const router = inject(Router);
    const websocketService = inject(WebsocketService);
    const token = authService.getToken();
    if (token === null) {
        return next(req);
    }
    if (req.url.includes('/api/auth/')) {
        return next(req);
    }
    const authReq = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
    });
    return next(authReq).pipe(
        catchError((err) => {
            if (err.status === 401) {
                authService.logout();
                websocketService.disconnect();
                router.navigate(['/login']);
            }
            return throwError(() => err);
        })
    )
};