import {Component, signal} from '@angular/core';
import {ChildComponent} from './child';

@Component({selector: 'demo-app', standalone: true, imports: [ChildComponent], templateUrl: './app.html'})
export class AppComponent {
  readonly title = signal('Angular Lite');
  readonly items = ['HTML', 'TypeScript', 'Templates inline'];
  greet() { return this.title(); }

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
