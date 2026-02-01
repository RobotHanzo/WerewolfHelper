import {Activity, LayoutGrid, LogOut, Moon, Settings} from 'lucide-react';
import {useTranslation} from '../lib/i18n';
import {ThemeToggle} from './ThemeToggle';
import {useAuth} from '../contexts/AuthContext';
import {useLocation} from 'react-router-dom';

interface SidebarProps {
    onLogout: () => void;
    onSettingsClick: () => void;
    onDashboardClick: () => void;
    onSpectatorClick: () => void;
    onSpeechClick: () => void;
    onSwitchServer: () => void;
    onToggleSpectatorMode: () => void;
    isSpectatorMode: boolean;
    isConnected: boolean;
}

export const Sidebar: React.FC<SidebarProps> = ({
                                                    onLogout,
                                                    onSettingsClick,
                                                    onDashboardClick,
                                                    onSpectatorClick,
                                                    onSpeechClick,
                                                    onSwitchServer,
                                                    onToggleSpectatorMode,
                                                    isSpectatorMode,
                                                    isConnected
                                                }) => {
    const {t} = useTranslation();
    const {user} = useAuth();
    const location = useLocation();

    const isDashboardActive = location.pathname.endsWith(user?.guildId ? `/server/${user.guildId}` : '/') && !location.pathname.includes('/settings') && !location.pathname.includes('/spectator') && !location.pathname.includes('/speech');
    const isSettingsActive = location.pathname.includes('/settings');
    const isSpectatorActive = location.pathname.includes('/spectator');
    const isSpeechActive = location.pathname.includes('/speech');

    return (
        <aside
            className="w-full md:w-64 bg-slate-100 dark:bg-slate-950 border-r border-slate-300 dark:border-slate-800 flex flex-col shrink-0">
            <div className="p-6 flex items-center gap-3 border-b border-slate-300 dark:border-slate-800">
                <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center">
                    <Moon className="w-5 h-5 text-white"/>
                </div>
                <span className="font-bold text-lg text-slate-900 dark:text-slate-100 tracking-tight">
          {t('app.title').split('助手')[0]}<span className="text-indigo-500 dark:text-indigo-400">助手</span>
        </span>
            </div>

            <nav className="flex-1 p-4 space-y-1">
                {user?.role === 'JUDGE' && !isSpectatorMode && (
                    <>
                        <button
                            onClick={onDashboardClick}
                            className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${isDashboardActive
                                ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
                            }`}
                        >
                            <Activity className="w-5 h-5"/>
                            <span className="font-medium">{t('sidebar.dashboard')}</span>
                        </button>
                        <button
                            onClick={onSettingsClick}
                            className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${isSettingsActive
                                ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
                            }`}
                        >
                            <Settings className="w-5 h-5"/>
                            <span className="font-medium">{t('sidebar.gameSettings')}</span>
                        </button>
                    </>
                )}

                {(user?.role === 'JUDGE' || user?.role === 'SPECTATOR') && (
                    <>
                        <button
                            onClick={onSpectatorClick}
                            className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${isSpectatorActive
                                ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
                            }`}
                        >
                            <Moon className="w-5 h-5"/>
                            <span className="font-medium">{t('sidebar.spectator')}</span>
                        </button>
                        <button
                            onClick={onSpeechClick}
                            className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg border transition-colors ${isSpeechActive
                                ? 'bg-indigo-100 dark:bg-indigo-600/10 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-600/20'
                                : 'text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-800 border-transparent'
                            }`}
                        >
                            <Activity className="w-5 h-5"/>
                            <span className="font-medium">{t('sidebar.speechManager')}</span>
                        </button>
                    </>
                )}

            </nav>

            <div className="p-4 border-t border-slate-300 dark:border-slate-800 space-y-3">
                {/* User Profile */}
                {user && (
                    <div className="flex items-center gap-3 px-2 py-2 bg-slate-200/50 dark:bg-slate-800/50 rounded-lg">
                        <img
                            src={user.avatar ? `https://cdn.discordapp.com/avatars/${user.userId}/${user.avatar}.png?size=64` : `https://cdn.discordapp.com/embed/avatars/${parseInt(user.userId) % 5}.png`}
                            alt={user.username}
                            className="w-10 h-10 rounded-full ring-2 ring-indigo-200 dark:ring-indigo-900"
                        />
                        <div className="flex-1 min-w-0">
                            <p className="text-sm font-medium text-slate-900 dark:text-white truncate">
                                {user.username}
                            </p>
                            {user.role === 'JUDGE' ? (
                                <button
                                    onClick={onToggleSpectatorMode}
                                    className={`inline-block px-2 py-0.5 text-xs font-medium rounded transition-colors cursor-pointer ${isSpectatorMode
                                        ? 'bg-blue-100 dark:bg-blue-950 text-blue-700 dark:text-blue-300 hover:bg-blue-200 dark:hover:bg-blue-900'
                                        : 'bg-purple-100 dark:bg-purple-950 text-purple-700 dark:text-purple-300 hover:bg-purple-200 dark:hover:bg-purple-900'
                                    }`}
                                    title={isSpectatorMode ? t('sidebar.backToJudge') : t('sidebar.viewAsSpectator')}
                                >
                                    {isSpectatorMode ? t('userRoles.SPECTATOR') : t('userRoles.JUDGE')}
                                </button>
                            ) : (
                                <span
                                    className={`inline-block px-2 py-0.5 text-xs font-medium rounded ${user.role === 'SPECTATOR'
                                        ? 'bg-blue-100 dark:bg-blue-950 text-blue-700 dark:text-blue-300'
                                        : 'bg-gray-100 dark:bg-gray-900 text-gray-700 dark:text-gray-300'
                                    }`}>
                  {t(`userRoles.${user.role}`) || user.role}
                </span>
                            )}
                        </div>
                    </div>
                )}

                {/* Connection Status */}
                <div className="flex items-center gap-3 px-2">
                    <div
                        className={`w-2 h-2 rounded-full ${isConnected ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'} `}/>
                    <span
                        className={`text-xs font-medium ${isConnected ? 'text-emerald-600 dark:text-emerald-500' : 'text-red-600 dark:text-red-500'} uppercase tracking-wider`}>
            {isConnected ? t('sidebar.botConnected') : t('sidebar.botDisconnected')}
          </span>
                </div>

                {/* Action Buttons */}
                <div className="flex items-center gap-2">
                    <ThemeToggle/>
                    <button
                        onClick={onSwitchServer}
                        className="p-3 text-slate-500 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-lg transition-colors"
                        title={t('sidebar.switchServer')}
                    >
                        <LayoutGrid className="w-5 h-5"/>
                    </button>
                    <button onClick={onLogout}
                            className="flex-1 flex items-center justify-center gap-2 px-4 py-2 text-slate-500 dark:text-slate-500 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/10 rounded-lg transition-colors">
                        <LogOut className="w-4 h-4"/>
                        <span className="font-medium text-sm">{t('sidebar.signOut')}</span>
                    </button>
                </div>
            </div>
        </aside>
    );
};
