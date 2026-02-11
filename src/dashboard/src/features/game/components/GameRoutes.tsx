import { Route, Routes } from 'react-router-dom';
import { Users } from 'lucide-react';
import { useTranslation } from '@/lib/i18n';
import { Session, Player } from '@/api/types.gen';
import { PlayerCard } from '@/features/players/components/PlayerCard';
import { SpectatorView } from '@/features/spectator/components/SpectatorView';
import { SpeechManager } from '@/features/speech/components/SpeechManager';
import { GameSettingsPage } from './GameSettingsPage';
import { MainDashboard } from './MainDashboard';

interface GameRoutesProps {
  guildId: string;
  gameState: Session;
  readonly?: boolean;
  onPlayerAction: (playerId: number, actionType: string) => void;
  players: Player[];
}

export const GameRoutes = ({
  guildId,
  gameState,
  readonly = false,
  onPlayerAction,
  players,
}: GameRoutesProps) => {
  const { t } = useTranslation();

  return (
    <Routes>
      <Route
        path="/"
        element={
          <MainDashboard
            guildId={guildId}
            session={gameState}
            readonly={readonly}
            players={players}
          />
        }
      />
      <Route
        path="/players"
        element={
          <>
            <div className="flex items-center justify-between mb-2">
              <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                <Users className="w-5 h-5 text-slate-500 dark:text-slate-400" />
                {t('players.management')}{' '}
                <span className="text-slate-500 dark:text-slate-500 text-sm font-normal">
                  ({players.filter((p) => p.alive).length} {t('players.alive')})
                </span>
              </h2>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
              {players.map((player) => (
                <PlayerCard
                  key={player.id}
                  player={player}
                  onAction={onPlayerAction}
                  readonly={readonly}
                />
              ))}
            </div>
          </>
        }
      />
      <Route
        path="/spectator"
        element={
          <SpectatorView players={players} doubleIdentities={gameState.doubleIdentities ?? false} />
        }
      />
      <Route
        path="/speech"
        element={
          <SpeechManager
            speech={(gameState.stateData as any)?.speech}
            police={(gameState.stateData as any)?.police}
            players={players}
            guildId={guildId}
            readonly={readonly}
          />
        }
      />
      <Route path="/settings" element={<GameSettingsPage />} />
    </Routes>
  );
};
