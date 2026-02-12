import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { History, LogOut, Play } from 'lucide-react';
import { useAuth } from '@/features/auth/contexts/AuthContext';
import { useTranslation } from '@/lib/i18n';

export const ModeSelectionPage: React.FC = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { user, logout, loading: authLoading } = useAuth();

  useEffect(() => {
    if (!authLoading && !user) {
      navigate('/login');
    }
  }, [user, authLoading, navigate]);

  if (authLoading) {
    return (
      <div className="min-h-screen bg-background-dark flex items-center justify-center">
        <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin"></div>
      </div>
    );
  }

  if (!user) return null;

  return (
    <div className="min-h-screen bg-background-light dark:bg-background-dark flex items-center justify-center p-6 relative overflow-hidden">
      {/* Background Ambient Glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-4xl h-96 bg-primary/10 blur-[120px] rounded-full pointer-events-none z-0"></div>

      <div className="w-full max-w-4xl z-10">
        <div className="text-center mb-12">
          <h1 className="text-4xl font-display font-bold text-white mb-4 tracking-tight">
            {t('replays.selection.welcome', { username: user?.username || 'Player' })}
          </h1>
          <p className="text-gray-400 max-w-lg mx-auto">{t('replays.selection.subtitle')}</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Manage Ongoing Games */}
          <button
            onClick={() => navigate('/manage')}
            className="group relative h-64 bg-surface-dark border border-white/5 rounded-2xl p-8 flex flex-col items-center justify-center transition-all duration-300 hover:border-primary/50 hover:scale-[1.02] overflow-hidden"
          >
            <div className="absolute inset-0 bg-gradient-to-br from-primary/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity"></div>
            <div className="w-20 h-20 rounded-2xl bg-primary/20 flex items-center justify-center mb-6 shadow-neon group-hover:scale-110 transition-transform">
              <Play className="w-10 h-10 text-white fill-white" />
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">
              {t('replays.selection.liveSession')}
            </h2>
            <p className="text-gray-400 text-center text-sm">
              {t('replays.selection.liveSessionDesc')}
            </p>
          </button>

          {/* View Replays */}
          <button
            onClick={() => navigate('/replays')}
            className="group relative h-64 bg-surface-dark border border-white/5 rounded-2xl p-8 flex flex-col items-center justify-center transition-all duration-300 hover:border-secondary/50 hover:scale-[1.02] overflow-hidden"
          >
            <div className="absolute inset-0 bg-gradient-to-br from-secondary/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity"></div>
            <div className="w-20 h-20 rounded-2xl bg-secondary/20 flex items-center justify-center mb-6 shadow-neon-pink group-hover:scale-110 transition-transform">
              <History className="w-10 h-10 text-white" />
            </div>
            <h2 className="text-2xl font-bold text-white mb-2">
              {t('replays.selection.replayLibrary')}
            </h2>
            <p className="text-gray-400 text-center text-sm">
              {t('replays.selection.replayLibraryDesc')}
            </p>
          </button>
        </div>

        <div className="mt-12 flex justify-center">
          <button
            onClick={() => logout()}
            className="flex items-center gap-2 text-gray-500 hover:text-white transition-colors uppercase tracking-widest text-xs font-bold"
          >
            <LogOut className="w-4 h-4" />
            {t('replays.selection.logout')}
          </button>
        </div>
      </div>
    </div>
  );
};
