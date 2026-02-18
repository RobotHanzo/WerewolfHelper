import {
  Activity,
  LayoutGrid,
  LogOut,
  Mic,
  Moon,
  Settings,
  Settings2,
  Sun,
  Users,
  X,
} from 'lucide-react';
import { useTranslation } from '@/lib/i18n';
import { ThemeToggle } from '@/components/ui/ThemeToggle';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { useLocation, useNavigate } from 'react-router-dom';
import { DiscordAvatar, DiscordName } from '@/components/DiscordUser';
import { Player } from '@/api/types.gen';
import { GAME_STEPS } from '@/features/game/constants';

interface SidebarProps {
  onLogout: () => void;
  onSettingsClick: () => void;
  onDashboardClick: () => void;
  onPlayersClick: () => void;
  onSpectatorClick: () => void;
  onSpeechClick: () => void;
  onSwitchServer: () => void;
  onToggleSpectatorMode: () => void;
  isSpectatorMode: boolean;
  isConnected: boolean;

  // Game Header Relocated Props
  dayCount: number;
  timerSeconds: number;
  speech?: any;
  players?: Player[];
  currentStep?: string;
  currentState?: string;
  guildId?: string;
  isManualStep?: boolean;

  // Mobile
  isOpen?: boolean;
  onClose?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  onLogout,
  onSettingsClick,
  onDashboardClick,
  onPlayersClick,
  onSpectatorClick,
  onSpeechClick,
  onSwitchServer,
  onToggleSpectatorMode,
  isSpectatorMode,
  isConnected,
  dayCount,
  timerSeconds,
  speech,
  players,
  currentStep,
  currentState,
  guildId,
  isManualStep = false,
  isOpen = false,
  onClose,
}) => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const isDashboardActive =
    location.pathname.endsWith(user?.user?.guildId ? `/server/${user.user.guildId}` : '/') &&
    !location.pathname.includes('/settings') &&
    !location.pathname.includes('/spectator') &&
    !location.pathname.includes('/speech') &&
    !location.pathname.includes('/players');
  const isPlayersActive = location.pathname.includes('/players');
  const isSettingsActive = location.pathname.includes('/settings');
  const isSpectatorActive = location.pathname.includes('/spectator');
  const isSpeechActive = location.pathname.includes('/speech');

  const currentSpeaker =
    speech?.currentSpeakerId && players
      ? players.find((p) => p.id === speech.currentSpeakerId)
      : null;

  const displayDay = dayCount === 0 && currentState !== 'SETUP' && !!currentState ? 1 : dayCount;
  const isNight = currentState?.includes('NIGHT');
  const isLobby = currentState === 'SETUP' || !currentState;

  return (
    <aside
      className={`fixed inset-y-0 left-0 z-50 w-64 bg-slate-100 dark:bg-slate-950 border-r border-slate-300 dark:border-slate-800 flex flex-col shrink-0 transition-transform duration-300 ease-in-out md:relative md:translate-x-0 ${
        isOpen ? 'translate-x-0 shadow-2xl' : '-translate-x-full'
      }`}
    >
      <div className="p-6 flex items-center justify-between border-b border-slate-300 dark:border-slate-800">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center">
            <Moon className="w-5 h-5 text-white" />
          </div>
          <span className="font-bold text-lg text-slate-900 dark:text-slate-100 tracking-tight">
            {t('app.title').split('助手')[0]}
            <span className="text-indigo-500 dark:text-indigo-400">助手</span>
          </span>
        </div>
        {/* Mobile Close Button */}
        <button
          onClick={onClose}
          className="md:hidden p-2 text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="p-4 border-b border-slate-300 dark:border-slate-800 space-y-4">
        {/* Game Status */}
        <div className="bg-white dark:bg-slate-900 rounded-xl p-3 border border-slate-200 dark:border-slate-800 shadow-sm">
          <div className="flex items-center gap-2 mb-2 text-slate-900 dark:text-slate-100">
            {isLobby ? (
              <Settings2 className="w-4 h-4 text-slate-500 dark:text-slate-400" />
            ) : isNight ? (
              <Moon className="w-4 h-4 text-indigo-500 dark:text-indigo-400" />
            ) : (
              <Sun className="w-4 h-4 text-orange-500" />
            )}
            <span className="font-bold text-sm">
              {!isLobby && displayDay > 0 && t('game.day', { day: String(displayDay) }) + ' - '}
              {(() => {
                const stepInfo = GAME_STEPS.find((s) => s.id === currentStep);
                return stepInfo ? t(stepInfo.key) : currentStep || 'LOBBY';
              })()}
            </span>
          </div>

          {!isManualStep && !isLobby && (
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider">
                {t('game.timer')}
              </span>
              <div
                className={`font-mono font-bold text-lg ${timerSeconds < 10 ? 'text-red-500 dark:text-red-400' : 'text-slate-800 dark:text-slate-200'}`}
              >
                {Math.floor(timerSeconds / 60)}:{String(timerSeconds % 60).padStart(2, '0')}
              </div>
            </div>
          )}
        </div>
      </div>

      <nav className="flex-1 p-4 space-y-1 overflow-y-auto overflow-x-hidden">
        {user?.user?.role === 'JUDGE' && !isSpectatorMode && (
          <>
            <button
              onClick={onDashboardClick}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${
                isDashboardActive
                  ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
              }`}
            >
              <Activity className="w-5 h-5" />
              <span className="font-medium">{t('sidebar.dashboard')}</span>
            </button>
            <button
              onClick={onPlayersClick}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${
                isPlayersActive
                  ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
              }`}
            >
              <Users className="w-5 h-5" />
              <span className="font-medium">{t('sidebar.playersManagement')}</span>
            </button>
            <button
              onClick={onSettingsClick}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${
                isSettingsActive
                  ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
              }`}
            >
              <Settings className="w-5 h-5" />
              <span className="font-medium">{t('sidebar.gameSettings')}</span>
            </button>
          </>
        )}

        {(user?.user?.role === 'JUDGE' || user?.user?.role === 'SPECTATOR') && (
          <>
            <button
              onClick={onSpectatorClick}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${
                isSpectatorActive
                  ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
              }`}
            >
              <Moon className="w-5 h-5" />
              <span className="font-medium">{t('sidebar.spectator')}</span>
            </button>
            <button
              onClick={onSpeechClick}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${
                isSpeechActive
                  ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                  : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
              }`}
            >
              <Mic className="w-5 h-5" />
              <span className="font-medium">{t('sidebar.speechManager')}</span>
            </button>
          </>
        )}
      </nav>

      <div className="p-4 border-t border-slate-300 dark:border-slate-800 space-y-3">
        {/* User Profile */}
        {user && (
          <div className="space-y-3">
            {currentSpeaker && (
              <button
                onClick={() => navigate(`/server/${guildId}/speech`, { replace: true })}
                className="w-full flex flex-col items-center gap-1 p-3 bg-indigo-50 dark:bg-indigo-950/30 border border-indigo-200 dark:border-indigo-800/50 rounded-xl transition-all hover:bg-indigo-100 dark:hover:bg-indigo-950/50 group"
              >
                <div className="flex items-center gap-2 text-indigo-600 dark:text-indigo-400">
                  <Mic className="w-3 h-3 animate-pulse" />
                  <span className="text-[10px] font-bold uppercase tracking-wider">
                    {t('messages.speaking')}
                  </span>
                </div>
                <span className="font-bold text-sm text-slate-900 dark:text-slate-100">
                  {currentSpeaker.nickname}
                </span>
                {speech?.endTime && (
                  <span className="text-xs font-mono text-indigo-600 dark:text-indigo-400 bg-indigo-100/50 dark:bg-indigo-900/30 px-2 py-0.5 rounded-full border border-indigo-200/50 dark:border-indigo-800/30 mt-1">
                    {(() => {
                      const seconds = Math.max(0, Math.ceil((speech.endTime - Date.now()) / 1000));
                      return `${Math.floor(seconds / 60)
                        .toString()
                        .padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
                    })()}
                  </span>
                )}
              </button>
            )}

            <div className="flex items-center gap-3 px-2 py-2 bg-slate-200/50 dark:bg-slate-800/50 rounded-lg">
              <DiscordAvatar
                userId={user.user.userId}
                guildId={user.user.guildId}
                avatarClassName="w-10 h-10 rounded-full ring-2 ring-indigo-200 dark:ring-indigo-900"
              />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-slate-900 dark:text-white truncate">
                  <DiscordName userId={user.user.userId} guildId={user.user.guildId} />
                </p>
                {user.user.role === 'JUDGE' ? (
                  <button
                    onClick={onToggleSpectatorMode}
                    className={`inline-block px-2 py-0.5 text-xs font-medium rounded transition-colors cursor-pointer ${
                      isSpectatorMode
                        ? 'bg-blue-100 dark:bg-blue-950 text-blue-700 dark:text-blue-300 hover:bg-blue-200 dark:hover:bg-blue-900'
                        : 'bg-purple-100 dark:bg-purple-950 text-purple-700 dark:text-purple-300 hover:bg-purple-200 dark:hover:bg-purple-900'
                    }`}
                    title={
                      isSpectatorMode ? t('sidebar.backToJudge') : t('sidebar.viewAsSpectator')
                    }
                  >
                    {isSpectatorMode ? t('userRoles.SPECTATOR') : t('userRoles.JUDGE')}
                  </button>
                ) : (
                  <span
                    className={`inline-block px-2 py-0.5 text-xs font-medium rounded ${
                      user.user.role === 'SPECTATOR'
                        ? 'bg-blue-100 dark:bg-blue-950 text-blue-700 dark:text-blue-300'
                        : 'bg-gray-100 dark:bg-gray-900 text-gray-700 dark:text-gray-300'
                    }`}
                  >
                    {t(`userRoles.${user.user.role}`) || user.user.role}
                  </span>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Connection Status */}
        <div className="flex items-center gap-3 px-2">
          <div
            className={`w-2 h-2 rounded-full ${isConnected ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'} `}
          />
          <span
            className={`text-xs font-medium ${isConnected ? 'text-emerald-600 dark:text-emerald-500' : 'text-red-600 dark:text-red-500'} uppercase tracking-wider`}
          >
            {isConnected ? t('sidebar.botConnected') : t('sidebar.botDisconnected')}
          </span>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <button
            onClick={onSwitchServer}
            className="p-3 text-slate-500 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-lg transition-colors"
            title={t('sidebar.switchServer')}
          >
            <LayoutGrid className="w-5 h-5" />
          </button>
          <button
            onClick={onLogout}
            className="flex-1 flex items-center justify-center gap-2 px-4 py-2 text-slate-500 dark:text-slate-500 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/10 rounded-lg transition-colors"
          >
            <LogOut className="w-4 h-4" />
            <span className="font-medium text-sm">{t('sidebar.signOut')}</span>
          </button>
        </div>
      </div>
    </aside>
  );
};
