import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, {
  providers: [provideHttpClient(), provideRouter([
    { path: '', redirectTo: 'play', pathMatch: 'full' },
    { path: 'play', loadComponent: () => import('./app/play/play.component').then(m => m.PlayComponent) },
    { path: 'history', loadComponent: () => import('./app/history/history.component').then(m => m.HistoryComponent) },
    { path: 'settings', loadComponent: () => import('./app/settings/settings.component').then(m => m.SettingsComponent) },
    { path: '**', redirectTo: 'play' }
  ])]
}).catch(error => console.error('Não foi possível iniciar o Chess Studio.', error));
