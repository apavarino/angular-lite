import {Component, input, output} from '@angular/core';

@Component({selector: 'demo-child', standalone: true, template: `<strong>{{ label() }}</strong>`})
export class ChildComponent {
  readonly from = input('');
  readonly label = input('Bonjour');
  readonly saved = output<void>();
}
