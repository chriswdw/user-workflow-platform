import ReactDOM from 'react-dom/client';
// Self-hosted fonts (bundled by Vite, no third-party requests). Imported before index.css so the
// @font-face rules exist when the --font / --mono stacks below reference them.
import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
import './index.css';
import { App } from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <App />
);
