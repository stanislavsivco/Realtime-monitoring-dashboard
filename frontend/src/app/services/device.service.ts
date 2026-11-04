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

export interface DeviceInput {
    name: string;
    type: string;
    location: string;
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

    deleteDevice(id: number): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${id}`);
    }

    createDevice(input: DeviceInput): Observable<DeviceDTO> {
        return this.http.post<DeviceDTO>(this.baseUrl, input);
    }

    updateDevice(id: number, input: DeviceInput): Observable<DeviceDTO> {
        return this.http.put<DeviceDTO>(`${this.baseUrl}/${id}`, input);
    }
}