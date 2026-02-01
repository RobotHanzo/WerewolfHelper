import { useState, useEffect } from 'react';
import { Server, Users, Loader2 } from 'lucide-react';
import { useTranslation } from '../lib/i18n';
import { api } from '../lib/api';

interface Session {
    guildId: string;
    guildName: string;
    guildIcon?: string;
    players: any[];
}

interface ServerSelectorProps {
    onSelectServer: (guildId: string) => void;
    onBack: () => void;
}

export const ServerSelector: React.FC<ServerSelectorProps> = ({ onSelectServer, onBack }) => {
    const { t } = useTranslation();
    const [sessions, setSessions] = useState<Session[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        loadSessions();
    }, []);

    const loadSessions = async () => {
        try {
            setLoading(true);
            setError(null);
            const response: any = await api.getSessions();
            console.log('API Response:', response);
            // Response is the array directly, not wrapped in {data: [...]}
            const sessionsArray = Array.isArray(response) ? response : (response.data || []);
            console.log('Sessions array:', sessionsArray);
            console.log('Sessions length:', sessionsArray.length);
            setSessions(sessionsArray);
        } catch (err: any) {
            console.error('Failed to load sessions:', err);
            setError(err.message || t('serverSelector.loadError'));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex items-center justify-center p-4 relative overflow-hidden">
            {/* Background Effects */}
            <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none">
                <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-indigo-900/20 dark:bg-indigo-900/20 rounded-full blur-[100px]" />
                <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-red-900/10 dark:bg-red-900/10 rounded-full blur-[100px]" />
            </div>

            <div className="w-full max-w-2xl bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl p-8 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-2xl z-10">
                <div className="flex flex-col items-center mb-6">
                    <div className="w-16 h-16 bg-gradient-to-br from-indigo-500 to-purple-600 rounded-2xl flex items-center justify-center shadow-lg mb-4">
                        <Server className="w-8 h-8 text-white" />
                    </div>
                    <h1 className="text-2xl font-bold text-slate-900 dark:text-white mb-2">{t('serverSelector.title')}</h1>
                    <p className="text-slate-600 dark:text-slate-400 text-center">{t('serverSelector.subtitle')}</p>
                </div>

                {loading && (
                    <div className="flex flex-col items-center justify-center py-12">
                        <Loader2 className="w-8 h-8 text-indigo-500 animate-spin mb-3" />
                        <p className="text-slate-600 dark:text-slate-400">{t('serverSelector.loading')}</p>
                    </div>
                )}

                {error && (
                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4 mb-4">
                        <p className="text-red-700 dark:text-red-400 text-sm">{error}</p>
                        <button
                            onClick={loadSessions}
                            className="mt-2 text-sm text-red-600 dark:text-red-400 hover:underline"
                        >
                            {t('serverSelector.retry')}
                        </button>
                    </div>
                )}

                {!loading && !error && sessions.length === 0 && (
                    <div className="text-center py-12">
                        <Server className="w-16 h-16 text-slate-300 dark:text-slate-600 mx-auto mb-4" />
                        <p className="text-slate-600 dark:text-slate-400 mb-2">{t('serverSelector.noSessions')}</p>
                        <p className="text-sm text-slate-500 dark:text-slate-500">
                            {t('serverSelector.noSessionsHint')}
                        </p>
                    </div>
                )}

                {!loading && !error && sessions.length > 0 && (
                    <div className="space-y-3 max-h-96 overflow-y-auto">
                        {sessions.map((session) => (
                            <button
                                key={session.guildId}
                                onClick={() => onSelectServer(session.guildId)}
                                className="w-full bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700 rounded-xl p-4 transition-all duration-200 flex items-center justify-between group"
                            >
                                <div className="flex items-center gap-4">
                                    <div className="w-12 h-12 bg-slate-200 dark:bg-slate-700 rounded-lg flex items-center justify-center overflow-hidden">
                                        {session.guildIcon ? (
                                            <img
                                                src={session.guildIcon}
                                                alt={session.guildName}
                                                className="w-full h-full object-cover"
                                            />
                                        ) : (
                                            <Server className="w-6 h-6 text-slate-500 dark:text-slate-400" />
                                        )}
                                    </div>
                                    <div className="text-left">
                                        <h3 className="font-semibold text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                                            {session.guildName}
                                        </h3>
                                        <div className="flex items-center gap-2 mt-1">
                                            <Users className="w-3 h-3 text-slate-500 dark:text-slate-400" />
                                            <span className="text-sm text-slate-600 dark:text-slate-400">
                                                {session.players?.length || 0} {t('serverSelector.players')}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                                <svg
                                    className="w-5 h-5 text-slate-400 dark:text-slate-500 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors"
                                    fill="none"
                                    viewBox="0 0 24 24"
                                    stroke="currentColor"
                                >
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                                </svg>
                            </button>
                        ))}
                    </div>
                )}

                <div className="mt-6 pt-6 border-t border-slate-200 dark:border-slate-700">
                    <button
                        onClick={onBack}
                        className="w-full bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 font-medium py-3 px-4 rounded-xl transition-all duration-200"
                    >
                        {t('serverSelector.backToLogin')}
                    </button>
                </div>
            </div>
        </div>
    );
};
