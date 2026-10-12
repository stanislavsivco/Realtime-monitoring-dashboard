import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DeviceDTO {
    id: number;
    name: string;
    type: string;
    location: string;
    status: string;
    simulated: boolean;
}

@Injectable({
    providedIn: 'root'
})
export class DeviceService {
    private readonly baseUrl = 'http://localhost:8080/api/devices';

    constructor(private http: HttpClient) { }

    getDevices(): Observable<DeviceDTO[]> {
        return this.http.get<DeviceDTO[]>(this.baseUrl);
    }
}