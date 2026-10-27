import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  imports: [],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  errorMessage = signal<string>('');

  constructor(private authService: AuthService, private router: Router) { }

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/dashboard']);
    }
  }

  onLogin(username: string, password: string): void {
    this.errorMessage.set('');
    if (username === '' || password === '') {
      this.errorMessage.set('Enter username and password');
      return;
    }
    this.authService.login(username, password).subscribe({
      next: (response) => {
        this.authService.saveSession(response);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        if (err.status === 401) {
          this.errorMessage.set('Wrong username or password');
        } else {
          this.errorMessage.set('Login failed, try again later');
        }
      }
    });
  }
}