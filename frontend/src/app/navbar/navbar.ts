import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { WebsocketService } from '../services/websocket.service';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
})
export class Navbar implements OnInit {
  username = signal<string | null>(null);
  role = signal<string | null>(null);

  constructor(private authService: AuthService, private websocketService: WebsocketService, private router: Router) { }

  ngOnInit(): void {
    this.username.set(this.authService.getUsername());
    this.role.set(this.authService.getRole());
  }

  isAdmin(): boolean {
    return this.role() === 'ADMIN';
  }

  logout(): void {
    this.authService.logout();
    this.websocketService.disconnect();
    this.router.navigate(['/login']);
  }
}