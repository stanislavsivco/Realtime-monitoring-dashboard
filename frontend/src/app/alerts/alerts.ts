import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AlertDTO, AlertService } from '../services/alert.service';

@Component({
  selector: 'app-alerts',
  imports: [RouterLink, DatePipe],
  templateUrl: './alerts.html',
  styleUrl: './alerts.scss',
})
export class Alerts implements OnInit {
  alerts = signal<AlertDTO[]>([]);
  errorMessage = signal<string>('');

  constructor(private alertService: AlertService) { }

  ngOnInit(): void {
    this.loadAlerts();
  }

  loadAlerts(): void {
    this.alertService.getAlerts(0, 10, '', '').subscribe({
      next: (page) => {
        this.alerts.set(page.content);
        this.errorMessage.set('');
      },
      error: () => {
        this.errorMessage.set('Could not load alerts');
      }
    });
  }

  getSeverityClass(severity: string): string {
    return severity.toLowerCase();
  }
}