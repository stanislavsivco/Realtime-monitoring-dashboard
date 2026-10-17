import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AlertDTO {
    id: number;
    deviceId: number;
    deviceName: string;
    severity: string;
    message: string;
    timestamp: string;
    acknowledged: boolean;
    resolved: boolean;
}

export interface AlertPage {
    content: AlertDTO[];
    totalPages: number;
    totalElements: number;
    number: number;
}

@Injectable({
    providedIn: 'root'
})
export class AlertService {
    private readonly baseUrl = 'http://localhost:8080/api/alerts';

    constructor(private http: HttpClient) { }

    getAlerts(page: number, size: number, severity: string, resolved: string): Observable<AlertPage> {
        let params = new HttpParams().set('page', page).set('size', size);
        if (severity !== '') {
            params = params.set('severity', severity);
        }
        if (resolved !== '') {
            params = params.set('resolved', resolved);
        }
        return this.http.get<AlertPage>(this.baseUrl, { params: params });
    }

    acknowledgeAlert(id: number): Observable<AlertDTO> {
        return this.http.patch<AlertDTO>(this.baseUrl + '/' + id + '/acknowledge', {});
    }

    resolveAlert(id: number): Observable<AlertDTO> {
        return this.http.patch<AlertDTO>(this.baseUrl + '/' + id + '/resolve', {});
    }
}