import { Component, OnInit, input, output, signal } from '@angular/core';
import { FormField, FormRoot, form, maxLength, required } from '@angular/forms/signals';
import { DeviceInput } from '../../services/device.service';

@Component({
  selector: 'app-device-form',
  imports: [FormField, FormRoot],
  templateUrl: './device-form.html',
  styleUrl: './device-form.scss',
})
export class DeviceForm implements OnInit {
  errorMessage = input<string>('');
  isSaving = input<boolean>(false);
  initialValue = input<DeviceInput | null>(null);
  saved = output<DeviceInput>();
  cancelled = output<void>();

  deviceTypes: string[] = ['Server', 'Database', 'Storage', 'Router'];

  model = signal<DeviceInput>({ name: '', type: '', location: '' });

  deviceForm = form(this.model, (path) => {
    required(path.name);
    maxLength(path.name, 255);
    required(path.type);
    required(path.location);
    maxLength(path.location, 255);
  }, {
    submission: {
      action: async () => {
        this.saved.emit(this.model());
      }
    }
  });

  cancel(): void {
    this.cancelled.emit();
  }

  ngOnInit(): void {
    const start = this.initialValue();
    if (start !== null) {
      if (this.deviceTypes.includes(start.type) === false) {
        this.deviceTypes.push(start.type);
      }
      this.model.set({ name: start.name, type: start.type, location: start.location });
    }
  }
}