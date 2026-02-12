import React, { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Gavel,
  LayoutGrid,
  Loader2,
  Moon,
  Pill,
  Settings,
  Share2,
  Sun,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from '@/lib/i18n';
import { getReplay } from '@/api';

export const ReplayViewPage: React.FC = () => {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [currentStep, setCurrentStep] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ['replay', sessionId],
    queryFn: () => getReplay({ path: { sessionId: sessionId! } }),
    enabled: !!sessionId,
  });

  const replay = (data as any)?.data?.data;

  // Flatten events into a linear sequence if needed, or just use the days
  const steps = useMemo(() => {
    if (!replay || !replay.timeline) return [];
    const allSteps: any[] = [];

    // Sort days numerically
    const dayNums = Object.keys(replay.timeline)
      .map(Number)
      .sort((a, b) => a - b);

    dayNums.forEach((dayNum) => {
      const dayData = replay.timeline[dayNum];
      allSteps.push({
        type: 'DAY_INTRO',
        day: dayNum,
        title: t('dashboard.day') + ` ${dayNum}`,
        isNight: false,
        events: [],
      });

      // Split into night and day if appropriate, or just show the whole "Day" object
      // For simplicity in this storyboard View, let's treat each Day/Night cycle as a card
      allSteps.push({
        type: 'CYCLE',
        day: dayNum,
        title:
          dayNum === 0
            ? t('replays.view.gameStart', 'GAME START')
            : t('dashboard.nightPhase', 'NIGHT') + ` ${dayNum}`,
        isNight: true,
        actions: dayData.nightActions,
        events: dayData.nightEvents,
      });

      if (dayData.dayEvents.length > 0) {
        allSteps.push({
          type: 'CYCLE',
          day: dayNum,
          title: t('dashboard.day') + ` ${dayNum} ` + t('replays.view.dayPhaseLabel'),
          isNight: false,
          events: dayData.dayEvents,
        });
      }
    });

    return allSteps;
  }, [replay]);

  const activeStep = steps[currentStep];

  if (isLoading) {
    return (
      <div className="h-screen bg-background-dark flex flex-col items-center justify-center">
        <Loader2 className="w-12 h-12 text-primary animate-spin mb-4" />
        <p className="text-gray-400 font-bold tracking-widest uppercase">
          {t('replays.view.loading')}
        </p>
      </div>
    );
  }

  if (error || !replay) {
    return (
      <div className="h-screen bg-background-dark flex flex-col items-center justify-center">
        <p className="text-red-400 font-bold mb-4">{t('replays.view.error')}</p>
        <button onClick={() => navigate('/replays')} className="text-primary hover:underline">
          {t('replays.view.backToLibrary')}
        </button>
      </div>
    );
  }

  return (
    <div className="bg-background-light dark:bg-background-dark font-display text-gray-800 dark:text-gray-100 h-screen flex flex-col overflow-hidden selection:bg-primary selection:text-white">
      {/* 1. Header & Replay Controls */}
      <header className="h-16 border-b border-primary/20 bg-surface-dark/90 backdrop-blur-md flex items-center justify-between px-6 z-20 shrink-0">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/replays')}
            className="p-2 rounded-lg hover:bg-primary/20 text-gray-400 hover:text-white transition-colors"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="font-bold text-lg tracking-wide uppercase text-white flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-secondary shadow-neon-pink animate-pulse"></span>
              {t('replays.view.titlePrefix')}
              {replay.sessionId.substring(0, 8)}
            </h1>
            <p className="text-xs text-gray-400 font-medium tracking-wider">
              {new Date(replay.startTime).toLocaleDateString()} • {replay.result} •{' '}
              {Math.round((replay.endTime - replay.startTime) / 60000)}
              {t('replays.view.durationLabel')}
            </p>
          </div>
        </div>

        {/* Timeline Progress */}
        <div className="hidden md:flex flex-col items-center w-1/3">
          <div className="flex justify-between w-full text-[10px] uppercase font-bold text-gray-500 mb-1 px-1">
            <span>{t('replays.view.timelineStart')}</span>
            <span className="text-primary">{activeStep?.title}</span>
            <span>{t('replays.view.timelineEnd')}</span>
          </div>
          <div className="w-full h-1.5 bg-background-dark rounded-full overflow-hidden relative">
            <div
              className="absolute left-0 top-0 bottom-0 bg-gradient-to-r from-primary/50 to-primary rounded-full shadow-neon transition-all duration-500"
              style={{ width: `${((currentStep + 1) / steps.length) * 100}%` }}
            ></div>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-darker border border-primary/20 hover:border-primary/50 text-xs font-semibold uppercase tracking-wider transition-all text-white">
            <Share2 className="w-4 h-4" />
            {t('replays.view.shareClip')}
          </button>
          <button className="bg-primary/10 hover:bg-primary/20 text-primary border border-primary/20 p-2 rounded-lg transition-colors">
            <Settings className="w-5 h-5" />
          </button>
        </div>
      </header>

      {/* Main Content Area: Split View */}
      <main className="flex-1 flex flex-col relative overflow-hidden">
        {/* Background Ambient Glow */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full max-w-4xl h-96 bg-primary/20 blur-[120px] rounded-full pointer-events-none z-0"></div>

        {/* 2. Storyboard Carousel Section */}
        <section className="relative z-10 flex-1 flex flex-col items-center justify-center py-8 min-h-[400px]">
          <div className="w-full max-w-7xl px-4 md:px-12 flex items-center justify-center gap-4 md:gap-8 h-full">
            {/* Prev Button */}
            <button
              disabled={currentStep === 0}
              onClick={() => setCurrentStep((prev) => prev - 1)}
              className="hidden md:flex flex-col items-center justify-center w-12 h-24 rounded-lg hover:bg-white/5 group transition-colors shrink-0 disabled:opacity-20"
            >
              <ChevronLeft className="w-12 h-12 text-gray-600 group-hover:text-primary transition-colors" />
            </button>

            {/* Active Card */}
            <div className="relative w-full max-w-lg md:w-[500px] h-[400px] shrink-0 transition-all duration-500 z-20 group">
              <div className="absolute -inset-0.5 bg-gradient-to-br from-primary via-secondary to-primary rounded-2xl opacity-75 blur-sm group-hover:opacity-100 transition duration-1000 animate-pulse"></div>
              <div className="relative w-full h-full bg-surface-dark rounded-2xl border border-white/10 flex flex-col overflow-hidden shadow-2xl">
                {/* Card Header */}
                <div className="h-24 bg-gradient-to-r from-primary/20 to-surface-dark p-6 flex items-center justify-between border-b border-white/5">
                  <div>
                    <h2 className="text-3xl font-display font-bold text-white tracking-tight flex items-center gap-3">
                      {activeStep?.title}
                      {activeStep?.isNight && (
                        <span className="text-xs bg-primary text-white px-2 py-0.5 rounded uppercase tracking-wider font-semibold">
                          {t('replays.view.critical')}
                        </span>
                      )}
                    </h2>
                    <p className="text-primary-300 text-sm font-medium mt-1">
                      {activeStep?.isNight
                        ? t('replays.view.nightPhaseLabel')
                        : t('replays.view.dayPhaseLabel')}
                    </p>
                  </div>
                  <div className="w-12 h-12 rounded-full bg-primary/20 flex items-center justify-center border border-primary/30 shadow-neon">
                    {activeStep?.isNight ? (
                      <Moon className="w-6 h-6 text-white text-2xl" />
                    ) : (
                      <Sun className="w-6 h-6 text-white text-2xl" />
                    )}
                  </div>
                </div>

                {/* Card Body */}
                <div className="flex-1 p-6 md:p-8 space-y-6 overflow-y-auto custom-scrollbar">
                  {/* Events */}
                  {activeStep?.events?.map((event: any, i: number) => (
                    <div key={i} className="flex gap-4 items-start">
                      <div className="mt-1 w-8 h-8 rounded-full bg-primary/10 border border-primary/30 flex items-center justify-center shrink-0 text-primary">
                        <Gavel className="w-4 h-4" />
                      </div>
                      <div className="p-3 rounded-lg bg-white/5 border border-white/5 w-full">
                        <p className="text-gray-300 leading-relaxed text-sm">
                          {event.type === 'POLL_END' && (
                            <>{t('replays.view.events.pollEnd', { title: event.details.title })}</>
                          )}
                          {event.type === 'POLICE_TRANSFER' && (
                            <>
                              {t('replays.view.events.policeTransfer', {
                                from: `Player ${event.details.from}`,
                                to: `Player ${event.details.to}`,
                              })}
                            </>
                          )}
                        </p>
                      </div>
                    </div>
                  ))}

                  {/* Role Actions Rendering */}
                  {activeStep?.actions?.map((action: any, i: number) => (
                    <div key={`act-${i}`} className="flex gap-4 items-start">
                      <div className="mt-1 w-8 h-8 rounded-full bg-secondary/10 border border-secondary/30 flex items-center justify-center shrink-0 text-secondary">
                        {action.actionId.includes('KILL') ? (
                          <Gavel className="w-4 h-4" />
                        ) : (
                          <Pill className="w-4 h-4" />
                        )}
                      </div>
                      <div className="p-3 rounded-lg bg-secondary/5 border border-secondary/10 w-full">
                        <p className="text-gray-200 leading-relaxed text-sm">
                          <span className="text-secondary font-bold uppercase text-[10px] block mb-1">
                            {action.actionId}
                          </span>
                          {t('replays.view.actions.targeted', {
                            actor: `Player ${action.actorId}`,
                            target: `Player ${action.targets[0]}`,
                          })}
                        </p>
                      </div>
                    </div>
                  ))}

                  {activeStep?.events?.length === 0 && activeStep?.actions?.length === 0 && (
                    <p className="text-gray-500 text-center py-12 italic text-sm">
                      {t('replays.view.emptyPhase')}
                    </p>
                  )}
                </div>

                {/* Card Footer */}
                <div className="h-14 bg-surface-darker/50 border-t border-white/5 flex items-center justify-between px-6">
                  <span className="text-xs text-gray-500 font-mono uppercase tracking-widest">
                    {t('replays.view.stepCounter', {
                      current: (currentStep + 1).toString(),
                      total: steps.length.toString(),
                    })}
                  </span>
                  <button className="text-xs text-primary hover:text-white flex items-center gap-1 font-semibold uppercase tracking-wider transition-colors">
                    {t('replays.view.viewFullLog')}
                  </button>
                </div>
              </div>
            </div>

            {/* Next Button */}
            <button
              disabled={currentStep === steps.length - 1}
              onClick={() => setCurrentStep((prev) => prev + 1)}
              className="hidden md:flex flex-col items-center justify-center w-12 h-24 rounded-lg hover:bg-white/5 group transition-colors shrink-0 disabled:opacity-20"
            >
              <ChevronRight className="w-12 h-12 text-gray-600 group-hover:text-primary transition-colors" />
            </button>
          </div>
        </section>

        {/* 3. Contextual Player Grid */}
        <section className="relative z-10 bg-surface-darker border-t border-white/5 flex-1 p-6 md:p-8 overflow-y-auto">
          <div className="max-w-7xl mx-auto h-full flex flex-col">
            <div className="flex items-center justify-between mb-6">
              <h3 className="text-sm font-bold text-gray-400 uppercase tracking-widest flex items-center gap-2">
                <LayoutGrid className="w-4 h-4" />{' '}
                {t('replays.view.boardStateLabel', { title: activeStep?.title })}
              </h3>
              <div className="flex items-center gap-4 text-xs font-medium text-gray-500">
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-gray-600"></span>{' '}
                  {t('replays.view.statusDead')}
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-secondary shadow-neon-pink"></span>{' '}
                  {t('replays.view.statusTargeted')}
                </div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-primary shadow-neon"></span>{' '}
                  {t('replays.view.statusActive')}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-4 w-full content-center">
              {Object.values(replay.players).map((player: any) => {
                const isDead = player.deathDay !== null && player.deathDay < activeStep?.day;
                const isDying = player.deathDay === activeStep?.day && !activeStep?.isNight;
                const isTarget = activeStep?.actions?.some((a: any) =>
                  a.targets.includes(player.id)
                );
                const isActor = activeStep?.actions?.some((a: any) => a.actorId === player.id);

                return (
                  <div
                    key={player.id}
                    className={`relative group transition-all duration-500 ${isDead || isDying ? 'opacity-40 grayscale' : ''}`}
                  >
                    <div
                      className={`aspect-square bg-surface-dark rounded-xl border flex flex-col items-center justify-center relative overflow-hidden transition-colors ${
                        isTarget
                          ? 'border-secondary shadow-neon-pink'
                          : isActor
                            ? 'border-primary shadow-neon'
                            : 'border-white/5'
                      }`}
                    >
                      {isTarget && (
                        <div className="absolute top-0 right-0 bg-secondary text-white text-[10px] font-bold px-1.5 py-0.5 rounded-bl-lg">
                          {t('replays.view.targetIndicator')}
                        </div>
                      )}
                      <div
                        className={`w-12 h-12 rounded-full bg-gray-800 mb-2 border-2 p-0.5 ${
                          isTarget
                            ? 'border-secondary'
                            : isActor
                              ? 'border-primary'
                              : 'border-transparent'
                        }`}
                      >
                        <img
                          className="w-full h-full object-cover rounded-full"
                          src={
                            player.avatarUrl ||
                            'https://lh3.googleusercontent.com/pw/AP1GczPrU_K...'
                          }
                          alt={player.username}
                        />
                        {(isDead || isDying) && (
                          <div className="absolute inset-0 flex items-center justify-center z-10 pointer-events-none">
                            <span className="material-icons-round text-4xl text-red-500/50 rotate-[-15deg]">
                              close
                            </span>
                          </div>
                        )}
                      </div>
                      <span className="text-[10px] font-bold text-gray-300 truncate w-full text-center px-1">
                        {player.username}
                      </span>
                      <span className="text-[9px] text-gray-500 uppercase tracking-wide mt-0.5">
                        {isDead || isDying
                          ? t('replays.view.deadAtDay', { day: player.deathDay.toString() })
                          : t('replays.view.statusAlive')}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </section>
      </main>
    </div>
  );
};
