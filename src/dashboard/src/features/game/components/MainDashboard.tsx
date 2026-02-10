import {useEffect, useMemo, useRef, useState} from 'react';
import {Mic, Play, Shuffle, SkipForward, Skull, StepForward, Sun, Users} from 'lucide-react';
import {useTranslation} from '@/lib/i18n';
import {useMutation} from '@tanstack/react-query';
import {
    assignRolesMutation,
    nextStateMutation,
    setStateMutation,
    startGameMutation
} from '@/api/@tanstack/react-query.gen';
import {Player, Session} from '@/api/types.gen';
import {SpeechManager} from '@/features/speech/components/SpeechManager';
import {VoteStatus} from './VoteStatus';
import {DiscordAvatar, DiscordName} from '@/components/DiscordUser';
import {NightStatus} from './NightStatus';
import {GAME_STEPS} from '../constants';

interface MainDashboardProps {
    guildId: string;
    gameState: Session;
    readonly?: boolean;
    players: Player[];
}

export const MainDashboard = ({
                                  guildId,
                                  gameState,
                                  readonly = false,
                                  players,
                              }: MainDashboardProps) => {
    const {t} = useTranslation();
    const [isWorking, setIsWorking] = useState(false);
    const [transitionClass, setTransitionClass] = useState('stage-enter-forward');
    const [isStageAnimating, setIsStageAnimating] = useState(false);
    const [lastWordsTimeLeft, setLastWordsTimeLeft] = useState(0);

    // Mutations
    const setState = useMutation(setStateMutation());
    const nextState = useMutation(nextStateMutation());
    const startGame = useMutation(startGameMutation());
    const assignRoles = useMutation(assignRolesMutation());

    const steps = useMemo(() => GAME_STEPS.map(step => ({
        id: step.id,
        name: t(step.key)
    })), [t]);

    const currentId = gameState.currentState || 'SETUP';
    const currentIndex = useMemo(() => steps.findIndex(step => step.id === currentId), [steps, currentId]);
    const previousIndexRef = useRef(currentIndex);
    const animationTimeoutRef = useRef<number | null>(null);

    useEffect(() => {
        const previousIndex = previousIndexRef.current;
        if (previousIndex !== currentIndex && currentIndex !== -1) {
            setTransitionClass(currentIndex > previousIndex ? 'stage-enter-forward' : 'stage-enter-back');
            previousIndexRef.current = currentIndex;
        }
    }, [currentIndex]);

    useEffect(() => {
        setIsStageAnimating(true);
        if (animationTimeoutRef.current) {
            window.clearTimeout(animationTimeoutRef.current);
        }
        animationTimeoutRef.current = window.setTimeout(() => {
            setIsStageAnimating(false);
            animationTimeoutRef.current = null;
        }, 400);

        return () => {
            if (animationTimeoutRef.current) {
                window.clearTimeout(animationTimeoutRef.current);
                animationTimeoutRef.current = null;
            }
        };
    }, [currentId]);

    const speech = (gameState.stateData as any)?.speech;
    const speechEndTime = speech?.endTime ? Number(speech.endTime) : 0;

    useEffect(() => {
        if (!speechEndTime || currentId !== 'DEATH_ANNOUNCEMENT') {
            setLastWordsTimeLeft(0);
            return;
        }

        const interval = window.setInterval(() => {
            const remaining = Math.max(0, Math.ceil((speechEndTime - Date.now()) / 1000));
            setLastWordsTimeLeft(remaining);
        }, 100);

        return () => window.clearInterval(interval);
    }, [speechEndTime, currentId]);

    const handleSetStep = async (stepId: string) => {
        if (readonly || isWorking) return;
        setIsWorking(true);
        try {
            await setState.mutateAsync({path: {guildId: guildId}, body: {stepId}});
        } finally {
            setIsWorking(false);
        }
    };

    const handleNextStep = async () => {
        if (readonly || isWorking) return;
        setIsWorking(true);
        try {
            await nextState.mutateAsync({path: {guildId: guildId}});
        } finally {
            setIsWorking(false);
        }
    };

    const handleStartGame = async () => {
        if (readonly || isWorking) return;
        setIsWorking(true);
        try {
            await startGame.mutateAsync({path: {guildId: guildId}});
        } finally {
            setIsWorking(false);
        }
    };

    const handleAssignRoles = async () => {
        if (readonly || isWorking) return;
        setIsWorking(true);
        try {
            await assignRoles.mutateAsync({path: {guildId: guildId}});
        } finally {
            setIsWorking(false);
        }
    };

    const renderStageContent = () => {
        switch (currentId) {
            case 'SETUP':
                return (
                    <div className="animate-in fade-in duration-300">
                        <div
                            className="p-6 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
                            <div className="flex items-center gap-3 mb-4">
                                <Users className="w-6 h-6 text-indigo-600 dark:text-indigo-400"/>
                                <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">
                                    {t('steps.setup')}
                                </h3>
                            </div>
                            <p className="text-sm text-slate-600 dark:text-slate-400 mb-4">
                                {t('dashboard.setupDescription', 'Configure game settings and assign roles to players')}
                            </p>
                            <div className="flex gap-2">
                                {!gameState.hasAssignedRoles && (
                                    <button
                                        onClick={handleAssignRoles}
                                        disabled={readonly || isWorking}
                                        className="px-4 py-2 rounded-lg bg-green-600 hover:bg-green-700 text-white font-medium disabled:opacity-50 transition-colors flex items-center gap-2"
                                    >
                                        <Shuffle className="w-4 h-4"/>
                                        {t('dashboard.assignRoles')}
                                    </button>
                                )}
                                <button
                                    onClick={handleStartGame}
                                    disabled={readonly || isWorking || !gameState.hasAssignedRoles}
                                    className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 disabled:bg-gray-400 text-white font-medium disabled:opacity-50 transition-colors flex items-center gap-2"
                                >
                                    <Play className="w-4 h-4"/>
                                    {t('dashboard.startGame')}
                                </button>
                            </div>
                        </div>
                    </div>
                );

            case 'NIGHT_PHASE':
                return (
                    <div className="animate-in fade-in duration-300 h-full overflow-hidden">
                        <NightStatus guildId={guildId} players={players} gameState={gameState}/>
                    </div>
                );

            case 'DAY_PHASE':
                return (
                    <div className="animate-in fade-in duration-300">
                        <div
                            className="p-6 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
                            <div className="flex items-center gap-3 mb-4">
                                <Sun className="w-6 h-6 text-orange-500"/>
                                <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">
                                    {t('steps.day')}
                                </h3>
                            </div>
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                {t('dashboard.dayPhaseDescription', 'Players discuss and prepare for voting')}
                            </p>
                        </div>
                    </div>
                );

            case 'SHERIFF_ELECTION':
            case 'SPEECH_PHASE':
                const currentSpeech = (gameState.stateData as any)?.speech;
                const currentPolice = (gameState.stateData as any)?.police;
                return (
                    <div className="animate-in fade-in duration-300 h-full">
                        {currentSpeech && players && (
                            <SpeechManager
                                guildId={guildId}
                                speech={currentSpeech as any}
                                police={currentPolice as any}
                                players={players}
                                readonly={readonly}
                            />
                        )}
                    </div>
                );

            case 'DEATH_ANNOUNCEMENT':
                const deadPlayers = (gameState.stateData?.deadPlayers || []).map((id: number) =>
                    players.find(p => p.id === id)
                ).filter((p: any) => p !== undefined);

                const lastWordsSpeaker = speech?.currentSpeakerId
                    ? players.find(p => p.id === speech.currentSpeakerId)
                    : undefined;
                const lastWordsTotal = speech?.totalTime || 0;
                const lastWordsRemainingMs = speechEndTime ? Math.max(0, speechEndTime - Date.now()) : 0;
                const lastWordsProgress = lastWordsTotal > 0 ? Math.max(0, Math.min(100, (lastWordsRemainingMs / lastWordsTotal) * 100)) : 0;
                return (
                    <div className="animate-in fade-in duration-300">
                        <div
                            className="p-6 rounded-2xl border border-slate-200 dark:border-slate-800 from-slate-50 via-white to-slate-100 dark:from-slate-900 dark:via-slate-900 dark:to-slate-800 shadow-sm">
                            <div className="flex items-center gap-3 mb-6">
                                <Skull className="w-6 h-6 text-red-600 dark:text-red-400"/>
                                <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100">
                                    {t('steps.deathAnnouncement')}
                                </h3>
                            </div>

                            {speechEndTime > 0 && lastWordsTimeLeft > 0 && (
                                <div
                                    className="mb-6 p-4 rounded-xl border border-indigo-200 dark:border-indigo-800/60 bg-white/70 dark:bg-slate-900/60">
                                    <div className="flex items-center gap-3">
                                        <div
                                            className="w-9 h-9 rounded-full bg-indigo-600/10 dark:bg-indigo-500/10 flex items-center justify-center">
                                            <Mic className="w-5 h-5 text-indigo-600 dark:text-indigo-400"/>
                                        </div>
                                        <div className="min-w-0">
                                            <div className="text-sm font-semibold text-slate-900 dark:text-slate-100">
                                                {t('game.lastWords')}
                                            </div>
                                            {lastWordsSpeaker && (
                                                <div className="text-xs text-slate-600 dark:text-slate-400 truncate">
                                                    {lastWordsSpeaker.nickname}
                                                </div>
                                            )}
                                        </div>
                                        <div
                                            className="ml-auto text-sm font-semibold text-indigo-700 dark:text-indigo-300">
                                            {t('dashboard.timeRemaining', 'Time left')}: {lastWordsTimeLeft}s
                                        </div>
                                    </div>
                                    <div
                                        className="mt-3 h-2 rounded-full bg-slate-200 dark:bg-slate-800 overflow-hidden">
                                        <div
                                            className="h-full bg-indigo-500 transition-all"
                                            style={{width: `${lastWordsProgress}%`}}
                                        />
                                    </div>
                                </div>
                            )}

                            {deadPlayers.length === 0 ? (
                                <div
                                    className="p-6 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 bg-white/60 dark:bg-slate-900/60">
                                    <p className="text-center text-slate-500 dark:text-slate-400">
                                        {t('dashboard.noDeaths', 'No one died last night')}
                                    </p>
                                </div>
                            ) : (
                                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                                    {deadPlayers.map((player: any) => (
                                        <div
                                            key={player.id}
                                            className="relative overflow-hidden rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 shadow-sm"
                                        >
                                            <div
                                                className="absolute inset-0 from-slate-100/60 to-slate-200/60 dark:from-slate-800/60 dark:to-slate-900/60"/>
                                            <div className="relative p-4 flex items-center gap-3">
                                                <div className="relative">
                                                    <div
                                                        className="w-12 h-12 rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden ring-2 ring-red-300/60 dark:ring-red-600/40">
                                                        <DiscordAvatar userId={player.userId} guildId={guildId}
                                                                       avatarClassName="w-full h-full object-cover"/>
                                                    </div>
                                                    <div
                                                        className="absolute -bottom-1 -right-1 w-6 h-6 rounded-full bg-red-600 text-white flex items-center justify-center shadow">
                                                        <Skull className="w-3 h-3"/>
                                                    </div>
                                                </div>
                                                <div className="min-w-0">
                                                    <div
                                                        className="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">
                                                        <DiscordName userId={player.userId} guildId={guildId}
                                                                     fallbackName={player.nickname}/>
                                                    </div>
                                                    <div className="text-xs text-slate-500 dark:text-slate-400">
                                                        {t('dashboard.eliminated', 'Eliminated')}
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="relative px-4 pb-4"/>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                );

            case 'VOTING_PHASE':
                const currentExpel = (gameState.stateData as any)?.expel;
                return (
                    <div className="animate-in fade-in duration-300 h-full">
                        {currentExpel && (
                            <VoteStatus
                                candidates={currentExpel.candidates || []}
                                endTime={currentExpel.endTime as any}
                                totalVoters={players ? players.filter(p => p.alive).length : undefined}
                                players={players || []}
                                title={t('steps.voting')}
                                guildId={guildId}
                            />
                        )}
                    </div>
                );

            default:
                return null;
        }
    };

    return (
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 w-full h-full">
            {/* Left: Stage Content Area */}
            <div className={`lg:col-span-3 overflow-y-auto ${isStageAnimating ? 'scrollbar-hide' : ''}`}>
                <div className="space-y-4">
                    <div key={currentId} className={`stage-transition ${transitionClass}`}>
                        {renderStageContent()}
                    </div>
                </div>
            </div>

            {/* Right: Stage Navigator */}
            <div className="space-y-4">
                {/* Navigation Controls */}
                <div
                    className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
                    <div
                        className="flex items-center gap-2 text-sm font-semibold text-slate-700 dark:text-slate-300 mb-3">
                        <StepForward className="w-4 h-4"/>
                        {t('dashboard.stepNavigator')}
                    </div>
                    {currentId === 'SETUP' ? (
                        <div className="space-y-2">
                            {!gameState.hasAssignedRoles && (
                                <button
                                    onClick={handleAssignRoles}
                                    disabled={readonly || isWorking}
                                    className="w-full px-3 py-2 rounded-lg bg-green-600 hover:bg-green-700 text-white text-sm font-medium disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                                >
                                    <Shuffle className="w-4 h-4"/>
                                    {t('dashboard.assignRoles')}
                                </button>
                            )}
                            <button
                                onClick={handleStartGame}
                                disabled={readonly || isWorking || !gameState.hasAssignedRoles}
                                className="w-full px-3 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 disabled:bg-gray-400 text-white text-sm font-medium disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                            >
                                <Play className="w-4 h-4"/>
                                {t('dashboard.startGame')}
                            </button>
                        </div>
                    ) : (
                        <button
                            onClick={handleNextStep}
                            disabled={readonly || isWorking}
                            className="w-full px-3 py-2 rounded-lg bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-800 dark:text-slate-200 text-sm font-medium disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                        >
                            <SkipForward className="w-4 h-4"/>
                            {t('dashboard.nextStep')}
                        </button>
                    )}
                </div>

                {/* Stage Buttons */}
                <div
                    className="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 space-y-2">
                    <div
                        className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">
                        {t('dashboard.allStages')}
                    </div>
                    {steps.map(step => {
                        const active = step.id === currentId;
                        return (
                            <button
                                key={step.id}
                                onClick={() => handleSetStep(step.id)}
                                disabled={readonly || isWorking}
                                className={`w-full px-3 py-2 rounded-lg border text-sm font-medium transition-all disabled:opacity-50 ${active
                                    ? 'bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 border-indigo-300 dark:border-indigo-700 shadow-md'
                                    : 'bg-slate-50 dark:bg-slate-800 text-slate-700 dark:text-slate-200 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-700'
                                }`}
                                title={step.id}
                            >
                                {step.name}
                            </button>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};
