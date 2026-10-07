import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { MetricService, MetricDTO } from '../services/metric.service';
import { WebsocketService } from '../services/websocket.service';
import { DeviceService, DeviceDTO } from '../services/device.service';

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
  selectedCategory = signal<string>('computers');
  selectedDeviceId = signal<number | null>(null);
  devices = signal<DeviceDTO[]>([]);
  private metricsSubscription?: Subscription;

  visibleDevices = computed(() => {
    const result: DeviceDTO[] = [];
    for (const device of this.devices()) {
      if (this.getCategory(device) === this.selectedCategory()) {
        result.push(device);
      }
    }
    return result;
  });

  selectedDevice = computed(() => {
    for (const device of this.devices()) {
      if (device.id === this.selectedDeviceId()) {
        return device;
      }
    }
    return null;
  });

  constructor(private metricService: MetricService, private websocketService: WebsocketService, private deviceService: DeviceService) { }

  ngOnInit(): void {
    this.websocketService.connect();

    this.deviceService.getDevices().subscribe({
      next: (deviceList) => {
        this.devices.set(deviceList);
        this.selectFirstDevice();
      },
      error: (err) => {
        console.error('Couldnt load the devices', err);
      }
    });

    this.metricsSubscription = this.websocketService.metrics$.subscribe((metric) => {
      if (metric.deviceId === this.selectedDeviceId()) {
        this.applyMetric(metric);
      }
    });
  }

  getCategory(device: DeviceDTO): string {
    if (device.type === 'Server' || device.type === 'Database' || device.type === 'Storage') {
      return 'servers';
    }
    if (device.type === 'Router') {
      return 'routers';
    }
    return 'computers';
  }

  getStatusClass(status: string): string {
    if (status === 'ONLINE') {
      return 'online';
    }
    if (status === 'WARNING') {
      return 'warning';
    }
    if (status === 'CRITICAL') {
      return 'critical';
    }
    return 'offline';
  }

  getSourceLabel(device: DeviceDTO): string {
    if (device.simulated) {
      return 'Simulated';
    }
    return 'Live agent';
  }

  getSourceClass(device: DeviceDTO): string {
    if (device.simulated) {
      return 'simulated';
    }
    return 'agent';
  }

  selectCategory(category: string): void {
    this.selectedCategory.set(category);
    const list = this.visibleDevices();
    if (list.length > 0) {
      this.selectDevice(list[0].id);
    } else {
      this.selectedDeviceId.set(null);
      this.resetMetrics();
    }
  }

  selectDevice(deviceId: number): void {
    this.selectedDeviceId.set(deviceId);
    this.resetMetrics();
    this.metricService.getLatestMetric(deviceId).subscribe({
      next: (metric) => {
        if (this.selectedDeviceId() === deviceId) {
          this.applyMetric(metric);
        }
      },
      error: (err) => {
        console.error('Couldnt load the metric', err);
      }
    });
  }

  private selectFirstDevice(): void {
    const categories = ['computers', 'servers', 'routers'];
    for (const category of categories) {
      this.selectedCategory.set(category);
      const list = this.visibleDevices();
      if (list.length > 0) {
        this.selectDevice(list[0].id);
        return;
      }
    }
    this.selectedCategory.set('computers');
  }

  private resetMetrics(): void {
    this.diskUsage.set(null);
    this.ramUsage.set(null);
    this.latencyMs.set(null);
    this.cpuUsage.set(null);
    this.networkInUsage.set(null);
    this.networkOutUsage.set(null);
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
