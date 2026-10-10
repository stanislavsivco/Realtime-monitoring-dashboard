import { Injectable } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { MetricDTO } from './metric.service';
import { DeviceDTO } from './device.service';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private client: Client;
  private metricsSubject = new Subject<MetricDTO>();
  metrics$ = this.metricsSubject.asObservable();
  private devicesSubject = new Subject<DeviceDTO>();
  devices$ = this.devicesSubject.asObservable();
  private deletedDevicesSubject = new Subject<number>();
  deletedDevices$ = this.deletedDevicesSubject.asObservable();

  constructor() {
    this.client = new Client({
      brokerURL: 'ws://localhost:8080/ws/websocket',
      onConnect: () => {
        this.client.subscribe('/topic/metrics', (message) => {
          this.metricsSubject.next(JSON.parse(message.body));
        });
        this.client.subscribe('/topic/devices', (message) => {
          this.devicesSubject.next(JSON.parse(message.body));
        });
        this.client.subscribe('/topic/devices/delete', (message) => {
          this.deletedDevicesSubject.next(JSON.parse(message.body));
        });
      }
    });
  }

  connect(): void {
    this.client.activate();
  }
}
