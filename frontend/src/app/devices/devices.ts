import { Component, OnInit, signal } from '@angular/core';
import { DeviceDTO, DeviceInput, DeviceService } from '../services/device.service';
import { getErrorMessage } from '../services/error-message';
import { DeviceForm } from './device-form/device-form';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-devices',
  imports: [DeviceForm],
  templateUrl: './devices.html',
  styleUrl: './devices.scss',
})
export class Devices implements OnInit {
  devices = signal<DeviceDTO[]>([]);
  errorMessage = signal<string>('');
  confirmingId = signal<number | null>(null);
  isDeleting = signal<boolean>(false);
  isFormOpen = signal<boolean>(false);
  isSaving = signal<boolean>(false);
  formError = signal<string>('');
  editingDevice = signal<DeviceDTO | null>(null);

  constructor(private deviceService: DeviceService) { }

  ngOnInit(): void {
    this.loadDevices();
  }

  loadDevices(): void {
    this.deviceService.getDevices().subscribe({
      next: (deviceList) => {
        this.devices.set(deviceList);
        this.errorMessage.set('');
      },
      error: (err) => {
        this.errorMessage.set(getErrorMessage(err));
      }
    });
  }

  getStatusClass(status: string): string {
    return status.toLowerCase();
  }

  getSourceLabel(device: DeviceDTO): string {
    if (device.simulated) {
      return 'Simulated';
    }
    return 'Live agent';
  }

  askDelete(id: number): void {
    this.errorMessage.set('');
    this.confirmingId.set(id);
  }

  cancelDelete(): void {
    this.confirmingId.set(null);
  }

  confirmDelete(id: number): void {
    this.isDeleting.set(true);
    this.deviceService.deleteDevice(id).subscribe({
      next: () => {
        this.isDeleting.set(false);
        this.confirmingId.set(null);
        this.loadDevices();
      },
      error: (err) => {
        this.isDeleting.set(false);
        this.confirmingId.set(null);
        this.errorMessage.set(getErrorMessage(err));
      }
    });
  }

  openForm(): void {
    this.errorMessage.set('');
    this.formError.set('');
    this.confirmingId.set(null);
    this.editingDevice.set(null);
    this.isFormOpen.set(true);
  }

  openEdit(device: DeviceDTO): void {
    this.errorMessage.set('');
    this.formError.set('');
    this.confirmingId.set(null);
    this.editingDevice.set(device);
    this.isFormOpen.set(true);
  }

  closeForm(): void {
    this.isFormOpen.set(false);
    this.editingDevice.set(null);
  }

  saveDevice(input: DeviceInput): void {
    this.isSaving.set(true);
    this.formError.set('');

    const editing = this.editingDevice();
    let request: Observable<DeviceDTO>;
    if (editing === null) {
      request = this.deviceService.createDevice(input);
    } else {
      request = this.deviceService.updateDevice(editing.id, input);
    }

    request.subscribe({
      next: () => {
        this.isSaving.set(false);
        this.isFormOpen.set(false);
        this.editingDevice.set(null);
        this.loadDevices();
      },
      error: (err) => {
        this.isSaving.set(false);
        this.formError.set(getErrorMessage(err));
      }
    });
  }
}