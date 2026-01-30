import { createRoot } from 'react-dom/client';
import { ThemeProvider } from './lib/ThemeProvider';
import App from './App';
import './index.css';

const root = createRoot(document.getElementById('root')!);
root.render(
    <ThemeProvider>
        <App />
    </ThemeProvider>
);
