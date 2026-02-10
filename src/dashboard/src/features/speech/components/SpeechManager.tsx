import {ArrowDown, ArrowUp, Mic, Play, Shield, Square, UserMinus, UserPlus} from 'lucide-react';
import {useTranslation} from '@/lib/i18n';
import {useMutation} from '@tanstack/react-query';
import {
    confirmSpeechMutation,
    interruptSpeechMutation,
    setSpeechOrderMutation,
    skipSpeechMutation,
    startAutoSpeechMutation,
    startPoliceEnrollMutation
} from '@/api/@tanstack/react-query.gen';
import {Player} from '@/api/types.gen';
import {SpeakerCard} from './SpeakerCard';
import {DiscordAvatar, DiscordName} from '@/components/DiscordUser';
import {VoteStatus} from '@/features/game/components/VoteStatus';
import {Timer} from '@/components/ui/Timer';

// Local interfaces for states missing from SDK
export interface SpeechState {
    order: number[]; // List of Player IDs (internal IDs)
    currentSpeakerId?: number;
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
    speech?: SpeechState;
    police?: PoliceState;
    players: Player[];
    guildId: string;
    readonly?: boolean;
}

export const SpeechManager = ({speech, police, players, guildId, readonly = false}: SpeechManagerProps) => {
    const {t} = useTranslation();

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
    // Mutations
    const skipSpeech = useMutation(skipSpeechMutation());
    const interruptSpeech = useMutation(interruptSpeechMutation());
    const confirmOrder = useMutation(confirmSpeechMutation());
    const startAutoSpeech = useMutation(startAutoSpeechMutation());
    const startPoliceEnroll = useMutation(startPoliceEnrollMutation());
    const setSpeechOrder = useMutation(setSpeechOrderMutation());

    const currentSpeaker = speech?.currentSpeakerId ? players.find(p => p.id === speech.currentSpeakerId) : null;
    const isPaused = speech?.isPaused || false;
    const speechEndTime = speech?.endTime ? Number(speech.endTime) : 0;

    const isPoliceSelecting = speech && speech.currentSpeakerId === -1 && (!speech.order || speech.order.length === 0);
    const isSpeechActive = speech && (speech.currentSpeakerId !== undefined || (speech.order && speech.order.length > 0) || isPoliceSelecting);
    const isPoliceActive = police && (police.allowEnroll || police.allowUnEnroll || police.state === 'VOTING');
    const isActive = isSpeechActive || isPoliceActive;

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
                                    <span className="text-xs text-slate-400">{t('speechManager.waiting')}</span>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>
        );
    };

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

    if (police?.state === 'VOTING') {
        return (
            <div
                className="h-full flex flex-col p-4 gap-6 overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-500 items-center justify-center">
                <div
                    className="w-full max-w-2xl bg-white dark:bg-slate-800 rounded-2xl shadow-xl p-8 border border-slate-200 dark:border-slate-700">
                    <VoteStatus
                        candidates={police.candidates.filter(c => !c.quit)}
                        totalVoters={players.filter(p => p.alive && !police.candidates.some(c => c.id === p.id)).length}
                        endTime={police.stageEndTime as any}
                        players={players}
                        title={t('vote.policeElection')}
                        guildId={guildId}
                    />
                </div>
            </div>
        );
    }

    if (isPoliceActive && !isSpeechActive) {
        const isUnenrollment = police?.state === 'UNENROLLMENT';

        return (
            <div
                className="h-full flex flex-col p-4 gap-6 overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-500 items-center justify-center">
                <div className="text-center mb-8">
                    <Shield className="w-16 h-16 text-amber-500 mx-auto mb-4 animate-bounce"/>
                    <h2 className="text-2xl font-bold text-slate-800 dark:text-slate-100">
                        {isUnenrollment ? t('speechManager.policeUnenrollment') : t('speechManager.policeEnrollment')}
                    </h2>
                    {police?.stageEndTime && (
                        <div className="mt-4 flex justify-center">
                            <Timer
                                endTime={Number(police.stageEndTime)}
                                label={isUnenrollment ? t('speechManager.unenrollTime', 'Unenrollment Ends') : t('speechManager.enrollTime', 'Enrollment Ends')}
                            />
                        </div>
                    )}
                </div>

                <div className="flex gap-4 mb-8">
                    <div
                        className={`px-4 py-2 rounded-lg border ${police?.allowEnroll ? 'bg-green-100 border-green-300 text-green-700 dark:bg-green-900/30 dark:border-green-800 dark:text-green-400' : 'bg-red-100 border-red-300 text-red-700 dark:bg-red-900/30 dark:border-red-800 dark:text-red-400'}`}>
                        <div className="flex items-center gap-2">
                            {police?.allowEnroll ? <UserPlus className="w-4 h-4"/> : <Square className="w-3 h-3"/>}
                            <span
                                className="font-bold">{t('speechManager.allowEnroll')}: {police?.allowEnroll ? 'YES' : 'NO'}</span>
                        </div>
                    </div>
                    <div
                        className={`px-4 py-2 rounded-lg border ${police?.allowUnEnroll ? 'bg-green-100 border-green-300 text-green-700 dark:bg-green-900/30 dark:border-green-800 dark:text-green-400' : 'bg-red-100 border-red-300 text-red-700 dark:bg-red-900/30 dark:border-red-800 dark:text-red-400'}`}>
                        <div className="flex items-center gap-2">
                            {police?.allowUnEnroll ? <UserMinus className="w-4 h-4"/> : <Square className="w-3 h-3"/>}
                            <span
                                className="font-bold">{t('speechManager.allowUnEnroll')}: {police?.allowUnEnroll ? 'YES' : 'NO'}</span>
                        </div>
                    </div>
                </div>

                <div className="w-full max-w-2xl">
                    <h3 className="text-lg font-bold text-slate-700 dark:text-slate-300 mb-4 text-center">{t('speechManager.candidates')}</h3>
                    {police?.candidates && police.candidates.length > 0 ? (
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            {police.candidates.map(candidate => {
                                const p = players.find(x => x.id === candidate.id);
                                return (
                                    <div key={candidate.id}
                                         className="flex flex-col items-center p-3 bg-white dark:bg-slate-800 rounded-xl shadow border border-slate-200 dark:border-slate-700 animate-in zoom-in-50">
                                        <DiscordAvatar userId={p?.userId}
                                                       avatarClassName="w-12 h-12 rounded-full mb-2"/>
                                        <span className="font-medium text-slate-800 dark:text-slate-200">
                                            <DiscordName userId={p?.userId}
                                                         fallbackName={p?.nickname || `Player ${candidate.id}`}/>
                                        </span>
                                    </div>
                                );
                            })}
                        </div>
                    ) : (
                        <div className="text-center text-slate-500 italic">{t('speechManager.noCandidates')}</div>
                    )}
                </div>
            </div>
        );
    }

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
                {isPoliceSelecting ? (
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
            {!readonly && !isSpeechActive && !isPoliceActive && (
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
