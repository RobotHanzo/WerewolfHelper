import { Route, Routes } from 'react-router-dom';
import { AuthCallback } from '@/features/auth/components/AuthCallback';
import { AccessDenied } from '@/features/auth/components/AccessDenied';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { ServerSelectionPage } from '@/features/server-selection/pages/ServerSelectionPage';
import { PlayerManager } from '@/features/game/components/PlayerManager';
import { ModeSelectionPage } from '@/features/replays/pages/ModeSelectionPage';
import { ReplayListPage } from '@/features/replays/pages/ReplayListPage';
import { ReplayViewPage } from '@/features/replays/pages/ReplayViewPage';

const App = () => {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallback />} />
      <Route path="/access-denied" element={<AccessDenied />} />
      <Route path="/" element={<ModeSelectionPage />} />
      <Route path="/manage" element={<ServerSelectionPage />} />
      <Route path="/replays" element={<ReplayListPage />} />
      <Route path="/replays/:sessionId" element={<ReplayViewPage />} />
      <Route path="/server/:guildId/*" element={<PlayerManager />} />
    </Routes>
  );
};

export default App;
