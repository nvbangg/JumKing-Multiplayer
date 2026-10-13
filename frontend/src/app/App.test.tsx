import { renderToStaticMarkup } from 'react-dom/server';
import { expect, test } from 'vitest';
import { App } from './App';

test('entry renders an accessible page without a backend connection', () => {
  expect(renderToStaticMarkup(<App />)).toContain('<h1>JumpKing</h1>');
});
