import { Injectable } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Subject } from 'rxjs';
import { MetricDTO } from './metric.service';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private client: Client;
  private metricsSubject = new Subject<MetricDTO>();
  metrics$ = this.metricsSubject.asObservable();

  constructor() {
    this.client = new Client({
      brokerURL: 'ws://localhost:8080/ws/websocket',
      onConnect: () => {
        this.client.subscribe('/topic/metrics', (message) => {
          this.metricsSubject.next(JSON.parse(message.body));
        });
      }
    });
  }

  connect(): void {
    this.client.activate();
  }
}
