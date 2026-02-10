import {ArrowDown, ArrowUp, Mic, Play, Shield, Square, UserMinus, UserPlus} from 'lucide-react';
import {useTranslation} from '@/lib/i18n';
import {useMutation} from '@tanstack/react-query';
import {
    confirmSpeechMutation,
    interruptSpeechMutation,
    setSpeechOrderMutation,
    skipSpeechMutation,
    startAutoSpeechMutation,
    startPoliceEnrollMutation,
    stateActionMutation
} from '@/api/@tanstack/react-query.gen';
import {Player} from '@/api/types.gen';
import {SpeakerCard} from './SpeakerCard';
import {DiscordAvatar, DiscordName} from '@/components/DiscordUser';
import {VoteStatus} from '@/features/game/components/VoteStatus';
import {Timer} from '@/components/ui/Timer';

// Local interfaces for states missing from SDK
export interface SpeechState {
    order: number[]; // List of Player IDs (internal IDs)
    currentSpeakerId?: number | null;
    endTime: number;
    totalTime: number;
    isPaused?: boolean;
    interruptVotes?: number[];
}

export interface PoliceState {
    state: 'NONE' | 'ENROLLMENT' | 'SPEECH' | 'UNENROLLMENT' | 'VOTING' | 'FINISHED';
    stageEndTime?: number;
    allowEnroll: boolean;
    allowUnEnroll: boolean;
    candidates: {
        id: number;   // Player ID (internal)
        quit?: boolean;
        voters: string[]; // List of User IDs (strings)
    }[];
}

interface SpeechManagerProps {
    speech?: SpeechState | null;
    police?: PoliceState | null;
    players: Player[];
    guildId: string;
    readonly?: boolean;
}

export const SpeechManager = ({speech, police, players, guildId, readonly = false}: SpeechManagerProps) => {
    const {t} = useTranslation();

    // Mutations
    const skipSpeech = useMutation(skipSpeechMutation());
    const interruptSpeech = useMutation(interruptSpeechMutation());
    const confirmOrder = useMutation(confirmSpeechMutation());
    const startAutoSpeech = useMutation(startAutoSpeechMutation());
    const startPoliceEnroll = useMutation(startPoliceEnrollMutation());
    const setSpeechOrder = useMutation(setSpeechOrderMutation());
    const stateAction = useMutation(stateActionMutation());

    const handleSkip = () => {
        if (readonly) return;
        skipSpeech.mutate({path: {guildId}});
    };

    const handleInterrupt = () => {
        if (readonly) return;
        interruptSpeech.mutate({path: {guildId}});
    };

    const handleConfirmOrder = () => {
        if (readonly) return;
        confirmOrder.mutate({path: {guildId}});
    };

    const handleUnenroll = (playerId: number) => {
        if (readonly) return;
        stateAction.mutate({
            path: {guildId},
            body: {
                action: 'quit',
                data: {playerId}
            }
        });
    };

    const handleStartAutoSpeech = () => {
        if (readonly) return;
        startAutoSpeech.mutate({path: {guildId}});
    };

    const handleStartPoliceEnroll = () => {
        if (readonly) return;
        startPoliceEnroll.mutate({path: {guildId}});
    };

    const handleSetOrder = (direction: 'UP' | 'DOWN') => {
        if (readonly) return;
        setSpeechOrder.mutate({path: {guildId}, body: {direction}});
    };

    // Correctly handle null/undefined from SDK
    const currentSpeakerId = speech?.currentSpeakerId;
    const currentSpeaker = (currentSpeakerId != null && currentSpeakerId > 0)
        ? players.find(p => p.id === currentSpeakerId)
        : null;

    const isPaused = speech?.isPaused || false;
    const speechEndTime = speech?.endTime ? Number(speech.endTime) : 0;

    // Special state where someone (usually police) is selecting the speech order
    const isSelectingOrder = !!(speech && (currentSpeakerId === -1 || currentSpeakerId == null) && (!speech.order || speech.order.length === 0));

    // Core activity flags
    const isSpeechRunning = speech && (
        (currentSpeakerId != null && currentSpeakerId !== 0) ||
        (speech.order && speech.order.length > 0) ||
        isSelectingOrder
    );

    const hasPoliceElection = police && police.state !== 'NONE' && police.state !== 'FINISHED';
    const isActive = isSpeechRunning || hasPoliceElection;

    const activeCandidates = police?.candidates?.filter(c => !c.quit) || [];

    const renderSpeechOrder = () => {
        if (!speech?.order || speech.order.length === 0) return null;

        return (
            <div className="mt-6 w-full max-w-md">
                <h4 className="text-sm font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3 px-1">
                    {t('speechManager.remainingOrder')}
                </h4>
                <div className="flex flex-col gap-2">
                    {speech.order.map((pid, idx) => {
                        const player = players.find(p => p.id === pid);
                        if (!player) return null;

                        return (
                            <div key={`${pid}-${idx}`} className="relative w-full">
                                {idx > 0 && (
                                    <div
                                        className="absolute left-1/2 top-0 transform -translate-x-1/2 -translate-y-full h-2 w-0.5 bg-slate-300 dark:bg-slate-700"></div>
                                )}
                                <div
                                    className="w-full bg-slate-50 dark:bg-slate-900 p-3 rounded-lg border border-slate-200 dark:border-slate-700 flex items-center justify-between opacity-70">
                                    <div className="flex items-center gap-3 min-w-0">
                                        <span
                                            className="w-6 h-6 flex-shrink-0 flex items-center justify-center bg-slate-200 dark:bg-slate-800 rounded-full text-xs font-bold text-slate-500">
                                            {idx + 1}
                                        </span>
                                        <div className="flex items-center gap-2 min-w-0">
                                            <DiscordAvatar userId={player.userId}
                                                           avatarClassName="w-8 h-8 rounded-full flex-shrink-0"/>
                                            <span className="font-medium text-slate-700 dark:text-slate-300 truncate">
                                                {player.nickname}
                                            </span>
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <span className="text-xs text-slate-400">{t('speechManager.waiting')}</span>
                                        {!readonly && police?.state === 'SPEECH' && activeCandidates.some(c => c.id === pid) && (
                                            <button
                                                onClick={() => handleUnenroll(pid)}
                                                className="p-1.5 bg-slate-200 dark:bg-slate-700 hover:bg-red-100 dark:hover:bg-red-900/30 text-slate-500 hover:text-red-600 dark:hover:text-red-400 rounded-md transition-colors"
                                                title={t('vote.unenroll')}
                                            >
                                                <UserMinus className="w-4 h-4"/>
                                            </button>
                                        )}
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        );
    };

    // 1. If nothing is happening, show empty state
    if (!isActive) {
        return (
            <div className="flex flex-col items-center justify-center h-full p-8 text-center space-y-6">
                <div className="p-6 bg-slate-100 dark:bg-slate-800 rounded-full">
                    <Mic className="w-16 h-16 text-slate-400"/>
                </div>
                <h2 className="text-2xl font-bold text-slate-700 dark:text-slate-200">{t('sidebar.speechManager')}</h2>
                <p className="text-slate-500 max-w-md">
                    {readonly ? t('speechManager.noActiveSpeech') : t('speechManager.noActiveSpeechJudge')}
                </p>
                {!readonly && (
                    <div className="flex flex-col gap-3">
                        <button
                            onClick={handleStartAutoSpeech}
                            className="flex items-center gap-2 px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg shadow-lg transition-transform hover:scale-105"
                        >
                            <Play className="w-5 h-5"/>
                            {t('speechManager.startAuto')}
                        </button>
                        <button
                            onClick={handleStartPoliceEnroll}
                            className="flex items-center gap-2 px-6 py-3 bg-amber-600 hover:bg-amber-700 text-white rounded-lg shadow-lg transition-transform hover:scale-105"
                        >
                            <Shield className="w-5 h-5"/>
                            {t('speechManager.startPoliceEnroll')}
                        </button>
                    </div>
                )}
            </div>
        );
    }

    // 2. Voting takes absolute priority
    if (police?.state === 'VOTING') {
        const eligibleVoters = players.filter(p => p.alive && !activeCandidates.some(c => c.id === p.id)).map(p => p.id);
        return (
            <div
                className="h-full flex flex-col p-4 gap-6 overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-500 items-center justify-center">
                <div
                    className="w-full max-w-2xl bg-white dark:bg-slate-800 rounded-2xl shadow-xl p-8 border border-slate-200 dark:border-slate-700">
                    <VoteStatus
                        candidates={activeCandidates}
                        totalVoters={eligibleVoters.length}
                        endTime={police.stageEndTime as any}
                        players={players}
                        electorate={eligibleVoters}
                        title={t('steps.sheriffElectionPhase')}
                        subtitle={t('speechManager.candidates')}
                        guildId={guildId}
                    />
                </div>
            </div>
        );
    }

    // 3. Enrollment/Unenrollment phases
    if (police?.state === 'ENROLLMENT' || police?.state === 'UNENROLLMENT') {
        const isUnenrollment = police.state === 'UNENROLLMENT';

        return (
            <div
                className="h-full flex flex-col p-4 gap-8 overflow-y-auto scrollbar-hide animate-in fade-in duration-500">
                {/* Status Header Banner */}
                <section
                    className={`relative overflow-hidden rounded-2xl shadow-xl border backdrop-blur-xl animate-in fade-in slide-in-from-top-4 duration-700 fill-mode-both
                        ${isUnenrollment
                        ? 'bg-amber-50/50 dark:bg-amber-900/20 border-amber-200 dark:border-amber-900/30'
                        : 'bg-indigo-50/50 dark:bg-indigo-900/20 border-indigo-200 dark:border-indigo-900/30'}`}>

                    <div
                        className={`absolute inset-0 bg-gradient-to-r via-transparent to-transparent ${isUnenrollment ? 'from-amber-500/10' : 'from-indigo-500/10'}`}></div>

                    <div
                        className="p-6 md:p-8 relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
                        <div className="flex items-center gap-6">
                            <div className={`h-16 w-16 rounded-2xl flex items-center justify-center border shadow-lg animate-in fade-in zoom-in-75 duration-500 delay-100 fill-mode-both
                                ${isUnenrollment
                                ? 'bg-amber-500/20 border-amber-500/50 text-amber-600 dark:text-amber-400 shadow-amber-500/20'
                                : 'bg-indigo-500/20 border-indigo-500/50 text-indigo-600 dark:text-indigo-400 shadow-indigo-500/20'}`}>
                                <Shield className="w-8 h-8"/>
                            </div>
                            <div
                                className="animate-in fade-in slide-in-from-left-4 duration-500 delay-200 fill-mode-both">
                                <h2 className={`font-bold text-sm tracking-widest uppercase mb-1 ${isUnenrollment ? 'text-amber-600' : 'text-indigo-600'}`}>
                                    {isUnenrollment ? t('speechManager.policeUnenrollment') : t('speechManager.policeEnrollment')}
                                </h2>
                                <div className="flex items-baseline gap-3">
                                    <span className="text-3xl font-black text-slate-900 dark:text-white">
                                        {t('speechManager.collectingCandidates')}
                                    </span>
                                </div>
                                <div className="flex items-center gap-4 mt-3">
                                    <div className="flex items-center gap-2">
                                        <div
                                            className={`w-2 h-2 rounded-full ${police?.allowEnroll ? 'bg-green-500' : 'bg-red-500'}`}></div>
                                        <span
                                            className="text-xs font-bold text-slate-500 uppercase tracking-tighter">{t('speechManager.allowEnroll')}</span>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <div
                                            className={`w-2 h-2 rounded-full ${police?.allowUnEnroll ? 'bg-green-500' : 'bg-red-500'}`}></div>
                                        <span
                                            className="text-xs font-bold text-slate-500 uppercase tracking-tighter">{t('speechManager.allowUnEnroll')}</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {police?.stageEndTime && (
                            <div
                                className="flex-shrink-0 animate-in fade-in slide-in-from-right-4 duration-500 delay-300 fill-mode-both">
                                <Timer
                                    endTime={Number(police.stageEndTime)}
                                    size="md"
                                    label={isUnenrollment ? t('speechManager.unenrollTime') : t('speechManager.enrollTime')}
                                />
                            </div>
                        )}
                    </div>
                </section>

                {/* Candidate Grid */}
                <div className="space-y-6">
                    <div className="flex items-center justify-between px-2">
                        <h3 className="text-xl font-black text-slate-900 dark:text-white flex items-center gap-3">
                            <div
                                className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-800 flex items-center justify-center">
                                <UserPlus className="w-5 h-5 text-slate-500"/>
                            </div>
                            {t('speechManager.candidates')}
                            <span
                                className="ml-2 px-2 py-0.5 rounded-full bg-slate-100 dark:bg-slate-800 text-xs font-bold text-slate-500">
                                {activeCandidates.length}
                            </span>
                        </h3>
                    </div>

                    {activeCandidates.length > 0 ? (
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                            {activeCandidates.map((candidate, index) => {
                                const p = players.find(x => x.id === candidate.id);
                                return (
                                    <div key={candidate.id}
                                         style={{animationDelay: `${100 + index * 50}ms`}}
                                         className="group relative bg-white dark:bg-slate-900 rounded-2xl p-4 border border-slate-200 dark:border-white/10 shadow-sm hover:shadow-md transition-all duration-300 animate-in fade-in slide-in-from-bottom-4 fill-mode-both">
                                        <div className="flex items-center gap-4">
                                            <div className="relative">
                                                <div
                                                    className="absolute inset-0 bg-indigo-500/20 blur-lg rounded-full opacity-0 group-hover:opacity-100 transition-opacity"></div>
                                                <DiscordAvatar userId={p?.userId}
                                                               avatarClassName="w-14 h-14 rounded-2xl relative z-10 border-2 border-slate-100 dark:border-slate-800 shadow-sm"/>
                                            </div>
                                            <div className="min-w-0">
                                                <div
                                                    className="text-sm font-black text-slate-900 dark:text-white truncate">
                                                    <DiscordName userId={p?.userId}
                                                                 fallbackName={p?.nickname || `Player ${candidate.id}`}/>
                                                </div>
                                                <div className="flex items-center gap-2 mt-1">
                                                    <span
                                                        className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">{t('speechManager.candidate')}</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        <div
                            className="flex flex-col items-center justify-center py-20 px-4 rounded-3xl border-2 border-dashed border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-white/[0.02] animate-in fade-in duration-700">
                            <div
                                className="w-16 h-16 rounded-2xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center mb-4 text-slate-300 dark:text-slate-700">
                                <UserMinus className="w-8 h-8"/>
                            </div>
                            <h4 className="text-lg font-bold text-slate-400 dark:text-slate-600">{t('speechManager.noCandidates')}</h4>
                            <p className="text-sm text-slate-400 mt-1">{t('speechManager.waitingForCandidates', 'Expecting participants to step forward...')}</p>
                        </div>
                    )}
                </div>
            </div>
        );
    }

    // 4. Fallback to Speech View if active
    return (
        <div
            className="h-full flex flex-col p-4 gap-6 overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-500">
            <div
                className="flex justify-between items-center bg-white dark:bg-slate-800 p-4 rounded-xl shadow-sm border border-slate-200 dark:border-slate-700">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-indigo-100 dark:bg-indigo-900/50 rounded-lg">
                        <Mic className="w-6 h-6 text-indigo-600 dark:text-indigo-400"/>
                    </div>
                    <div>
                        <h3 className="font-bold text-slate-800 dark:text-slate-200">{t('speechManager.activeSpeech')}</h3>
                        <p className="text-xs text-slate-500">{t('speechManager.autoProcess')}</p>
                    </div>
                </div>
            </div>

            <div className="flex-1 overflow-y-auto scrollbar-hide min-h-0 flex flex-col items-center gap-8 py-8">
                {isSelectingOrder ? (
                    <div className="flex flex-col items-center gap-6 animate-in fade-in zoom-in-95">
                        <div className="relative">
                            <div
                                className="absolute inset-0 bg-yellow-400 blur-2xl opacity-20 rounded-full animate-pulse"></div>
                            <div
                                className="relative p-6 bg-white dark:bg-slate-800 rounded-2xl shadow-xl border-2 border-yellow-400 flex flex-col items-center gap-4">
                                <Shield className="w-16 h-16 text-yellow-500 animate-bounce"/>
                                <div className="text-center">
                                    <h3 className="text-2xl font-bold text-slate-800 dark:text-slate-100">{t('speechManager.waitingForPolice')}</h3>
                                    <p className="text-slate-500">{t('speechManager.waitingForPoliceSub')}</p>
                                </div>
                            </div>
                        </div>

                        {!readonly && (
                            <div className="flex flex-col gap-3 w-full max-w-xs">
                                <div
                                    className="text-xs text-center text-slate-400 uppercase font-bold tracking-wider mb-2">{t('speechManager.judgeOverride')}</div>
                                <button
                                    onClick={() => handleSetOrder('UP')}
                                    className="flex items-center justify-center gap-2 px-6 py-3 bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200 rounded-lg transition-colors border border-slate-200 dark:border-slate-600"
                                >
                                    <ArrowUp className="w-5 h-5"/>
                                    {t('speechManager.forceUp')}
                                </button>
                                <button
                                    onClick={() => handleSetOrder('DOWN')}
                                    className="flex items-center justify-center gap-2 px-6 py-3 bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200 rounded-lg transition-colors border border-slate-200 dark:border-slate-600"
                                >
                                    <ArrowDown className="w-5 h-5"/>
                                    {t('speechManager.forceDown')}
                                </button>
                            </div>
                        )}
                    </div>
                ) : (
                    <>
                        <div className="relative w-full max-w-md min-h-[350px] flex flex-col items-center">
                            {currentSpeaker ? (
                                <div
                                    key={currentSpeaker.id}
                                    className="w-full animate-in fade-in slide-in-from-bottom-4 duration-300"
                                >
                                    <SpeakerCard
                                        player={currentSpeaker}
                                        endTime={speechEndTime}
                                        isPaused={isPaused}
                                        t={t}
                                        readonly={readonly}
                                        onSkip={handleSkip}
                                        onInterrupt={handleInterrupt}
                                        onUnenroll={police?.state === 'SPEECH' && activeCandidates.some(c => c.id === currentSpeaker.id) ? () => handleUnenroll(currentSpeaker.id) : undefined}
                                    />
                                </div>
                            ) : (
                                <div className="text-slate-500 text-center mt-20">{t('speechManager.preparing')}</div>
                            )}
                            {renderSpeechOrder()}
                        </div>

                        {speech?.interruptVotes && speech.interruptVotes.length > 0 && (
                            <div
                                className="w-full max-w-md bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-900/30 rounded-lg p-4 animate-in slide-in-from-bottom-2 duration-300">
                                <h4 className="text-sm font-bold text-red-700 dark:text-red-400 mb-2 flex items-center gap-2">
                                    <Square className="w-4 h-4 animate-pulse"/>
                                    {t('speechManager.interruptVote')} ({speech.interruptVotes.length} / {Math.floor(players.filter(p => p.alive).length / 2) + 1})
                                </h4>
                                <div className="flex flex-wrap gap-2">
                                    {speech.interruptVotes.map(voterId => {
                                        const voter = players.find(p => p.id === voterId);
                                        return (
                                            <div key={voterId}
                                                 className="animate-in zoom-in-50 fade-in duration-300 flex items-center gap-1 bg-white dark:bg-slate-800 px-2 py-1 rounded text-xs border border-red-100 dark:border-red-900/50 shadow-sm">
                                                <DiscordAvatar userId={voter?.userId}
                                                               avatarClassName="w-4 h-4 rounded-full"/>
                                                <span className="text-slate-700 dark:text-slate-300">
                                                    <DiscordName userId={voter?.userId}
                                                                 fallbackName={voter?.nickname || String(voterId)}/>
                                                </span>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}
                    </>
                )}
            </div>
            {!readonly && !isSpeechRunning && (
                <div className="flex flex-wrap gap-3 justify-center mt-auto">
                    <button
                        onClick={handleConfirmOrder}
                        className="px-6 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-bold shadow-lg transition-all flex items-center gap-2 group"
                    >
                        <Play className="w-4 h-4 group-hover:scale-110 transition-transform"/>
                        {t('speechManager.startFlow')}
                    </button>
                </div>
            )}
        </div>
    );
};
