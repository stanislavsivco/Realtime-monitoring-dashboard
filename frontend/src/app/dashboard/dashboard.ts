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

  constructor(private metricService: MetricService) { }

  ngOnInit(): void {
    this.metricService.getLatestMetric(1).subscribe({
      next: (metric) => {
        this.diskUsage.set(metric.disk);
        this.ramUsage.set(metric.ram);
        this.latencyMs.set(metric.latencyMs);
      },
      error: (err) => {
        console.error('Nepodarilo sa nacitat metriku', err);
      }
    });
  }
}