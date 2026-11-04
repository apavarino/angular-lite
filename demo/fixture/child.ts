import {Component, input} from '@angular/core';

@Component({selector: 'demo-child', standalone: true, template: `<strong>{{ label() }}</strong>`})
export class ChildComponent {
  readonly from = input('');
  readonly label = input('Bonjour');
}
