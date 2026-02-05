import {createRoot} from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {ThemeProvider} from '@/lib/ThemeProvider';
import {AuthProvider} from '@/features/auth/contexts/AuthContext';
import {PlayerProvider} from '@/features/players/contexts/PlayerContext';
import App from './App';
import './index.css';

const root = createRoot(document.getElementById('root')!);
root.render(
    <ThemeProvider>
        <BrowserRouter>
            <AuthProvider>
                <PlayerProvider>
                    <App/>
                </PlayerProvider>
            </AuthProvider>
        </BrowserRouter>
    </ThemeProvider>
);
