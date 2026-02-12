import React, { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft,
  Calendar,
  ChevronLeft,
  ChevronRight,
  Clock,
  Gavel,
  LayoutGrid,
  Loader2,
  Moon,
  Settings,
  Share2,
  Sun,
} from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from '@/lib/i18n';
import { getReplay } from '@/api';
import { RoleTag } from '@/components/RoleTag';
import { getActionConfig } from '@/constants/gameData';

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
      // Day 0: Game start card only
      // Day 1+: Night card followed by Day card
      allSteps.push({
        type: 'CYCLE',
        day: dayNum,
        title:
          dayNum === 0
            ? t('replays.view.gameStart', 'GAME START')
            : t('replays.view.nightTitle', { day: dayNum.toString() }),
        isNight: true,
        actions: dayData.nightActions,
        events: dayData.nightEvents,
      });

      if (dayNum > 0 || dayData.dayEvents.length > 0) {
        allSteps.push({
          type: 'CYCLE',
          day: dayNum,
          title: t('replays.view.dayTitle', { day: dayNum.toString() }),
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
            <div className="flex items-center gap-4 mt-1 text-[10px] text-gray-500 font-bold uppercase tracking-widest">
              <span className="flex items-center gap-1.5">
                <Calendar className="w-3.5 h-3.5 text-gray-400" />
                {new Date(replay.startTime).toLocaleDateString()}
              </span>
              <span className="flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-gray-400" />
                {Math.round((replay.endTime - replay.startTime) / 60000)}{' '}
                {t('replays.list.durationSuffix')}
              </span>
              <span className="px-2 py-0.5 rounded bg-surface-darker border border-white/5 text-[9px] text-primary/80">
                {replay.result}
              </span>
            </div>
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
                          {event.type === 'POLL_START' && (
                            <>
                              {t('replays.view.events.pollStart', { title: event.details.title })}
                            </>
                          )}
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
                          {event.type === 'POLICE_ENROLL' && (
                            <>
                              {t('replays.view.events.policeEnroll', {
                                player: `Player ${event.details.playerId}`,
                              })}
                            </>
                          )}
                          {event.type === 'POLICE_UNENROLLED' && (
                            <>
                              {t('replays.view.events.policeUnenrolled', {
                                player: `Player ${event.details.playerId}`,
                              })}
                            </>
                          )}
                          {event.type === 'DISCUSSION_START' && (
                            <>{t('replays.view.events.discussionStart')}</>
                          )}
                          {event.type === 'DISCUSSION_END' && (
                            <>{t('replays.view.events.discussionEnd')}</>
                          )}
                        </p>
                      </div>
                    </div>
                  ))}

                  {/* Role Actions Rendering */}
                  {activeStep?.actions?.map((action: any, i: number) => {
                    const actionConfig = getActionConfig(action.actionDefinitionId || '');
                    return (
                      <div key={`act-${i}`} className="flex gap-4 items-start">
                        <div
                          className="mt-1 w-8 h-8 rounded-full flex items-center justify-center shrink-0 border border-white/20 shadow-lg text-white"
                          style={{ backgroundColor: actionConfig.color }}
                        >
                          <actionConfig.icon className="w-4 h-4" />
                        </div>
                        <div className="p-3 rounded-lg bg-white/5 border border-white/10 w-full group/act relative overflow-hidden">
                          <div
                            className="absolute left-0 top-0 bottom-0 w-1 opacity-50"
                            style={{ backgroundColor: actionConfig.color }}
                          ></div>
                          <p className="text-gray-200 leading-relaxed text-sm">
                            <span
                              className="font-bold uppercase text-[10px] block mb-1"
                              style={{ color: actionConfig.color }}
                            >
                              {t(
                                actionConfig.translationKey,
                                action.actionDefinitionId || 'Unknown Action'
                              )}
                            </span>
                            {t('replays.view.actions.targeted', {
                              actor: `Player ${action.actor}`,
                              target:
                                action.targets?.[0] !== undefined
                                  ? `Player ${action.targets[0]}`
                                  : 'None',
                            })}
                          </p>
                        </div>
                      </div>
                    );
                  })}

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
                  <button className="text-xs text-blue-400 hover:text-white flex items-center gap-1 font-semibold uppercase tracking-wider transition-colors">
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

                // Track actions this player is involved in
                const actionsAsTarget =
                  activeStep?.actions?.filter((a: any) => a.targets.includes(player.id)) || [];
                const actionsAsActor =
                  activeStep?.actions?.filter((a: any) => a.actor === player.id) || [];

                const isTarget = actionsAsTarget.length > 0;
                const isActor = actionsAsActor.length > 0;

                const primaryAction = actionsAsTarget[0] || actionsAsActor[0];
                const primaryColor = primaryAction
                  ? getActionConfig(primaryAction.actionDefinitionId || '').color
                  : null;

                return (
                  <div
                    key={player.id}
                    className={`relative group transition-all duration-500 ${isDead || isDying ? 'opacity-40 grayscale' : ''}`}
                  >
                    <div
                      className={`aspect-square bg-surface-dark rounded-xl border flex flex-col items-center justify-center relative overflow-hidden transition-all duration-500 ${
                        isTarget || isActor ? 'scale-105' : ''
                      }`}
                      style={{
                        borderColor: primaryColor || 'rgba(255,255,255,0.05)',
                        boxShadow: primaryColor ? `0 0 15px ${primaryColor}40` : 'none',
                      }}
                    >
                      {/* Multiple Action Indicators */}
                      <div className="absolute top-1 left-1 flex gap-0.5">
                        {actionsAsTarget.map((a: any, idx: number) => (
                          <div
                            key={`target-${idx}`}
                            className="w-1.5 h-1.5 rounded-full shadow-sm"
                            style={{
                              backgroundColor: getActionConfig(a.actionDefinitionId || '').color,
                            }}
                          />
                        ))}
                        {actionsAsActor.map((a: any, idx: number) => (
                          <div
                            key={`actor-${idx}`}
                            className="w-1.5 h-1.5 rounded-full border border-white/50"
                            style={{
                              backgroundColor: getActionConfig(a.actionDefinitionId || '').color,
                            }}
                          />
                        ))}
                      </div>

                      {isTarget && (
                        <div
                          className="absolute top-0 right-0 text-white text-[9px] font-bold px-1.5 py-0.5 rounded-bl-lg uppercase tracking-tighter"
                          style={{ backgroundColor: primaryColor || '#ec4899' }}
                        >
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
                      <span className="text-sm font-bold text-gray-100 truncate w-full text-center px-2 mt-1">
                        {player.username}
                      </span>
                      <div className="mt-1 flex flex-wrap gap-1 justify-center px-1">
                        {player.initialRoles?.map((role: string, idx: number) => (
                          <RoleTag
                            key={idx}
                            roleName={role}
                            isDead={isDead || isDying}
                            className="text-[9px]"
                          />
                        ))}
                        {(!player.initialRoles || player.initialRoles.length === 0) && (
                          <RoleTag
                            roleName={t('replays.view.statusAlive')}
                            isDead={isDead || isDying}
                          />
                        )}
                      </div>
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
