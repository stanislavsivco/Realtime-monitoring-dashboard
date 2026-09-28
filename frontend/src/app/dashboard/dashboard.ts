import { Component, OnInit, signal } from '@angular/core';
import { MetricService } from '../services/metric.service';

@Component({
  selector: 'app-dashboard',
  imports: [],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  diskUsage = signal<number | null>(null);
  ramUsage = signal<number | null>(null);
  latencyMs = signal<number | null>(null);
  cpuUsage = signal<number | null>(null);
  networkInUsage = signal<number | null>(null);
  networkOutUsage = signal<number | null>(null);

  constructor(private metricService: MetricService) { }

  ngOnInit(): void {
    this.metricService.getLatestMetric(1).subscribe({
      next: (metric) => {
        this.diskUsage.set(metric.disk);
        this.ramUsage.set(metric.ram);
        this.latencyMs.set(metric.latencyMs);
        this.cpuUsage.set(metric.cpu);
        this.networkInUsage.set(metric.networkInMbps);
        this.networkOutUsage.set(metric.networkOutMbps);
      },
      error: (err) => {
        console.error('Nepodarilo sa nacitat metriku', err);
      }
    });
  }
}