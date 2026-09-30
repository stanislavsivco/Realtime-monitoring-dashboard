import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { MetricService, MetricDTO } from '../services/metric.service';
import { WebsocketService } from '../services/websocket.service';

@Component({
  selector: 'app-dashboard',
  imports: [],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit, OnDestroy {
  diskUsage = signal<number | null>(null);
  ramUsage = signal<number | null>(null);
  latencyMs = signal<number | null>(null);
  cpuUsage = signal<number | null>(null);
  networkInUsage = signal<number | null>(null);
  networkOutUsage = signal<number | null>(null);
  private metricsSubscription?: Subscription;

  constructor(private metricService: MetricService, private websocketService: WebsocketService) { }

  ngOnInit(): void {
    this.websocketService.connect();

    this.metricService.getLatestMetric(1).subscribe({
      next: (metric) => this.applyMetric(metric),
      error: (err) => {
        console.error('Couldnt load the metric', err);
      }
    });

    this.websocketService.metrics$.subscribe((metric) => {
      if (metric.deviceId === 1) {
        this.applyMetric(metric);
      }
    });

    this.metricsSubscription = this.websocketService.metrics$.subscribe((metric) => {
      if (metric.deviceId === 1) {
        this.applyMetric(metric);
      }
    });
  }

  private applyMetric(metric: MetricDTO): void {
    this.diskUsage.set(metric.disk);
    this.ramUsage.set(metric.ram);
    this.latencyMs.set(metric.latencyMs);
    this.cpuUsage.set(metric.cpu);
    this.networkInUsage.set(metric.networkInMbps);
    this.networkOutUsage.set(metric.networkOutMbps);
  }

  ngOnDestroy(): void {
    this.metricsSubscription?.unsubscribe();
  }
}