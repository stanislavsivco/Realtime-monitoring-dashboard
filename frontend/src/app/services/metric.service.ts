import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MetricDTO {
    id: number;
    deviceId: number;
    timestamp: string;
    disk: number;
    ram: number;
    latencyMs: number;
}

@Injectable({
    providedIn: 'root'
})
export class MetricService {
    private readonly baseUrl = 'http://localhost:8080/api/devices';

    constructor(private http: HttpClient) { }

    getLatestMetric(deviceId: number): Observable<MetricDTO> {
        return this.http.get<MetricDTO>(`${this.baseUrl}/${deviceId}/metrics/latest`);
    }
}