import React, { StrictMode } from 'react';

import './i18n';

import { createRoot } from 'react-dom/client';

import Root from './app/root';
import { Providers } from './providers';

const root = document.getElementById('root');

if (process.env.NODE_ENV !== 'production') {
  import('@axe-core/react').then((axe) => {
    axe.default(React, root, 1000);
  });
}

createRoot(root!).render(
  <StrictMode>
    <Providers>
      <Root />
    </Providers>
  </StrictMode>,
);
