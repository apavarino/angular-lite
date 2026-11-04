import {Component, signal} from '@angular/core';
import {ChildComponent} from './child';
import {CommonModule} from '@angular/common';

@Component({selector: 'demo-app', standalone: true, imports: [ChildComponent, CommonModule], templateUrl: './app.html'})
export class AppComponent {
  hasMachin = true;
  criteria = {
    from: '2026-01-01',
  };
  readonly plainLabel = 'Example';
  readonly title = signal('Angular Lite');
  readonly items = ['HTML', 'TypeScript', 'Templates inline'];
  greet() { return this.title(); }
}

@Component({selector: 'demo-inline', standalone: true, template: `
  @if (visible()) {
    <p (click)="toggle()">{{ message }}</p>
  }
`})
export class InlineComponent {
  readonly visible = signal(true);
  message = 'Navigation depuis un template inline';
  toggle() { this.visible.update(value => !value); }
}
