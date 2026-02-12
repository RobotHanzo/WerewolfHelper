import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Calendar,
  Clock,
  History,
  Loader2,
  PlayCircle,
  Trash2,
  Trophy,
} from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from '@/lib/i18n';
import { deleteReplay, getReplays } from '@/api';

export const ReplayListPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { t } = useTranslation();

  const { data, isLoading, error } = useQuery({
    queryKey: ['replays'],
    queryFn: () => getReplays(),
  });

  const deleteMutation = useMutation({
    mutationFn: (sessionId: string) => deleteReplay({ path: { sessionId } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['replays'] });
    },
  });

  const replays = (data as any)?.data?.data || [];

  return (
    <div className="min-h-screen bg-background-light dark:bg-background-dark flex flex-col relative overflow-hidden">
      {/* Background Ambient Glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-4xl h-96 bg-primary/10 blur-[120px] rounded-full pointer-events-none z-0"></div>

      <header className="h-16 border-b border-primary/20 bg-surface-dark/90 backdrop-blur-md flex items-center justify-between px-6 z-20 shrink-0">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/')}
            className="p-2 rounded-lg hover:bg-primary/20 text-gray-400 hover:text-white transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="font-bold text-lg tracking-wide uppercase text-white">
              {t('replays.list.title')}
            </h1>
            <p className="text-[10px] text-gray-500 font-bold tracking-widest uppercase">
              {t('replays.list.subtitle')}
            </p>
          </div>
        </div>
      </header>

      <main className="flex-1 overflow-y-auto p-6 md:p-12 z-10">
        <div className="max-w-5xl mx-auto">
          {isLoading ? (
            <div className="flex flex-col items-center justify-center py-24">
              <Loader2 className="w-12 h-12 text-primary animate-spin mb-4" />
              <p className="text-gray-400 uppercase tracking-widest text-sm font-bold">
                {t('replays.list.loading')}
              </p>
            </div>
          ) : error ? (
            <div className="text-center py-24">
              <p className="text-red-400 font-bold">{t('replays.list.error')}</p>
            </div>
          ) : replays.length === 0 ? (
            <div className="text-center py-24 bg-surface-dark/50 border border-white/5 rounded-2xl">
              <History className="w-16 h-16 text-gray-600 mx-auto mb-4" />
              <h2 className="text-xl font-bold text-white mb-2">{t('replays.list.emptyTitle')}</h2>
              <p className="text-gray-500">{t('replays.list.emptyDesc')}</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-4">
              {replays.map((replay: any) => (
                <div
                  key={replay.sessionId}
                  className="group relative bg-surface-dark border border-white/5 rounded-xl p-6 flex flex-col md:flex-row items-center justify-between transition-all hover:border-primary/40 hover:bg-surface-dark/80"
                >
                  <div className="flex items-center gap-6 w-full md:w-auto">
                    <div className="w-16 h-16 rounded-xl bg-primary/10 flex items-center justify-center border border-primary/20 group-hover:shadow-neon transition-shadow">
                      <Trophy className="w-8 h-8 text-primary" />
                    </div>
                    <div>
                      <h3 className="text-lg font-bold text-white group-hover:text-primary transition-colors">
                        {t('replays.list.gameIdPrefix')}
                        {replay.sessionId.substring(0, 8)}
                      </h3>
                      <div className="flex flex-wrap items-center gap-4 mt-2 text-xs text-gray-500 font-medium uppercase tracking-wider">
                        <span className="flex items-center gap-1.5">
                          <Calendar className="w-3.5 h-3.5" />
                          {new Date(replay.startTime).toLocaleDateString()}
                        </span>
                        <span className="flex items-center gap-1.5">
                          <Clock className="w-3.5 h-3.5" />
                          {Math.round((replay.endTime - replay.startTime) / 60000)}
                          {t('replays.list.durationSuffix')}
                        </span>
                        <span className="px-2 py-0.5 rounded bg-surface-darker border border-white/5 text-[10px]">
                          {replay.result}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 mt-4 md:mt-0 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={() => deleteMutation.mutate(replay.sessionId)}
                      className="p-2.5 rounded-lg bg-red-500/10 text-red-400 border border-red-500/20 hover:bg-red-500/20 transition-colors"
                      title={t('replays.list.delete')}
                    >
                      <Trash2 className="w-5 h-5" />
                    </button>
                    <button
                      onClick={() => navigate(`/replays/${replay.sessionId}`)}
                      className="flex items-center gap-2 px-5 py-2.5 rounded-lg bg-primary text-white font-bold uppercase tracking-wider text-xs shadow-neon hover:scale-105 transition-all"
                    >
                      <PlayCircle className="w-4 h-4" />
                      {t('replays.list.watch')}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
};
