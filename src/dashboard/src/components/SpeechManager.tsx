import { useState, useEffect } from 'react';
import { Play, SkipForward, Square, Mic, Clock, Shield, ArrowUp, ArrowDown, UserPlus, UserMinus } from 'lucide-react';
import { Player, SpeechState, PoliceState } from '../types';
import { api } from '../lib/api';
import { useTranslation } from '../lib/i18n';

interface SpeechManagerProps {
    speech?: SpeechState;
    police?: PoliceState;
    players: Player[];
    guildId: string;
    readonly?: boolean;
}

export const SpeechManager = ({ speech, police, players, guildId, readonly = false }: SpeechManagerProps) => {
    const { t } = useTranslation();
    const [timeLeft, setTimeLeft] = useState(0);

    useEffect(() => {
        if (!speech || !speech.endTime) {
            setTimeLeft(0);
            return;
        }
        const interval = setInterval(() => {
            const remaining = Math.max(0, Math.ceil((speech.endTime - Date.now()) / 1000));
            setTimeLeft(remaining);
        }, 100);
        return () => clearInterval(interval);
    }, [speech?.endTime]);

    const handleStart = async () => {
        await api.startSpeech(guildId);
    };

    const handlePoliceEnroll = async () => {
        await api.startPoliceEnroll(guildId);
    };

    const handleSkip = async () => {
        await api.skipSpeech(guildId);
    };

    const handleInterrupt = async () => {
        await api.interruptSpeech(guildId);
    };

    const handleSetOrder = async (direction: 'UP' | 'DOWN') => {
        await api.setSpeechOrder(guildId, direction);
    };

    const isPoliceSelecting = speech && !speech.currentSpeakerId && (!speech.order || speech.order.length === 0);
    const isSpeechActive = speech && (speech.currentSpeakerId || (speech.order && speech.order.length > 0) || isPoliceSelecting);

    // Check if police enrollment is ACTIVELY happening (not just if candidates exist)
    // Only show police UI when enrollment or unenrollment is allowed
    const isPoliceActive = police && (police.allowEnroll || police.allowUnEnroll);

    const isActive = isSpeechActive || isPoliceActive;

    const currentSpeaker = speech?.currentSpeakerId ? players.find(p => p.id === speech.currentSpeakerId) : null;
    const nextPlayers = speech?.order ? speech.order.map(id => players.find(p => p.id === id)).filter(Boolean) as Player[] : [];

    // Animation State
    const [renderedSpeaker, setRenderedSpeaker] = useState<Player | null | undefined>(null);
    const [exitingSpeaker, setExitingSpeaker] = useState<Player | null | undefined>(null);
    const [speakerAnimation, setSpeakerAnimation] = useState<string>('animate-in fade-in zoom-in-95');

    // Sync renderedSpeaker with currentSpeaker on mount and updates
    useEffect(() => {
        // If we have a current speaker but nothing is rendered, initialize it immediately
        if (currentSpeaker && !renderedSpeaker && !exitingSpeaker) {
            setRenderedSpeaker(currentSpeaker);
            setSpeakerAnimation('animate-in fade-in zoom-in-95');
        }
    }, [currentSpeaker, renderedSpeaker, exitingSpeaker]);

    useEffect(() => {
        // When current speaker changes...
        if (currentSpeaker?.id !== renderedSpeaker?.id) {
            // If we have a currently rendered speaker, animate them out
            if (renderedSpeaker) {
                setExitingSpeaker(renderedSpeaker);
                setRenderedSpeaker(currentSpeaker);
                setSpeakerAnimation('animate-swipe-in');

                const timer = setTimeout(() => {
                    setExitingSpeaker(null);
                    // Reset animation to a stable state after transition
                    setSpeakerAnimation('animate-in fade-in zoom-in-95');
                }, 400); // Match animation duration
                return () => clearTimeout(timer);
            } else {
                // Otherwise just show the new one immediately
                setRenderedSpeaker(currentSpeaker);
                setSpeakerAnimation('animate-in fade-in zoom-in-95');
            }
        }
    }, [currentSpeaker, renderedSpeaker]);

    if (!isActive) {
        return (
            <div className="flex flex-col items-center justify-center h-full p-8 text-center space-y-6">
                <div className="p-6 bg-slate-100 dark:bg-slate-800 rounded-full">
                    <Mic className="w-16 h-16 text-slate-400" />
                </div>
                <h2 className="text-2xl font-bold text-slate-700 dark:text-slate-200">{t('sidebar.speechManager')}</h2>
                <p className="text-slate-500 max-w-md">
                    {readonly ? t('speechManager.noActiveSpeech') : t('speechManager.noActiveSpeechJudge')}
                </p>
                {!readonly && (
                    <div className="flex flex-col gap-3">
                        <button
                            onClick={handleStart}
                            className="flex items-center gap-2 px-6 py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg shadow-lg transition-transform hover:scale-105"
                        >
                            <Play className="w-5 h-5" />
                            {t('speechManager.startAuto')}
                        </button>
                        <button
                            onClick={handlePoliceEnroll}
                            className="flex items-center gap-2 px-6 py-3 bg-amber-600 hover:bg-amber-700 text-white rounded-lg shadow-lg transition-transform hover:scale-105"
                        >
                            <Shield className="w-5 h-5" />
                            {t('speechManager.startPoliceEnroll')}
                        </button>
                    </div>
                )}
            </div>
        );
    }

    // If Police Enrollment is active and no speech is active (or even if it is, show police view if appropriate? usually exclusive)
    // Assuming police enrollment happens before speech starts.
    if (isPoliceActive && !isSpeechActive) {
        return (
            <div className="h-full flex flex-col p-4 gap-6 overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-500 items-center justify-center">
                <div className="text-center mb-8">
                    <Shield className="w-16 h-16 text-amber-500 mx-auto mb-4 animate-bounce" />
                    <h2 className="text-2xl font-bold text-slate-800 dark:text-slate-100">{t('speechManager.policeEnrollment')}</h2>
                </div>

                <div className="flex gap-4 mb-8">
                    <div className={`px-4 py-2 rounded-lg border ${police?.allowEnroll ? 'bg-green-100 border-green-300 text-green-700' : 'bg-red-100 border-red-300 text-red-700'}`}>
                        <div className="flex items-center gap-2">
                            {police?.allowEnroll ? <UserPlus className="w-4 h-4" /> : <Square className="w-3 h-3" />}
                            <span className="font-bold">{t('speechManager.allowEnroll')}: {police?.allowEnroll ? 'YES' : 'NO'}</span>
                        </div>
                    </div>
                    <div className={`px-4 py-2 rounded-lg border ${police?.allowUnEnroll ? 'bg-green-100 border-green-300 text-green-700' : 'bg-red-100 border-red-300 text-red-700'}`}>
                        <div className="flex items-center gap-2">
                            {police?.allowUnEnroll ? <UserMinus className="w-4 h-4" /> : <Square className="w-3 h-3" />}
                            <span className="font-bold">{t('speechManager.allowUnEnroll')}: {police?.allowUnEnroll ? 'YES' : 'NO'}</span>
                        </div>
                    </div>
                </div>

                <div className="w-full max-w-2xl">
                    <h3 className="text-lg font-bold text-slate-700 dark:text-slate-300 mb-4 text-center">{t('speechManager.candidates')}</h3>
                    {police?.candidates && police.candidates.length > 0 ? (
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            {police.candidates.map(cid => {
                                const p = players.find(x => x.id === cid);
                                return (
                                    <div key={cid} className="flex flex-col items-center p-3 bg-white dark:bg-slate-800 rounded-xl shadow border border-slate-200 dark:border-slate-700 animate-in zoom-in-50">
                                        <img src={p?.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png'} className="w-12 h-12 rounded-full mb-2" />
                                        <span className="font-medium text-slate-800 dark:text-slate-200">{p?.name || `Player ${cid}`}</span>
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
        <div className="h-full flex flex-col p-4 gap-6 overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-500">
            <div className="flex justify-between items-center bg-white dark:bg-slate-800 p-4 rounded-xl shadow-sm border border-slate-200 dark:border-slate-700">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-indigo-100 dark:bg-indigo-900/50 rounded-lg">
                        <Mic className="w-6 h-6 text-indigo-600 dark:text-indigo-400" />
                    </div>
                    <div>
                        <h3 className="font-bold text-slate-800 dark:text-slate-200">{t('speechManager.activeSpeech')}</h3>
                        <p className="text-xs text-slate-500">{t('speechManager.autoProcess')}</p>
                    </div>
                </div>
            </div>

            <div className="flex-1 overflow-y-auto min-h-0 flex flex-col items-center gap-8 py-8">

                {isPoliceSelecting ? (
                    <div className="flex flex-col items-center gap-6 animate-in fade-in zoom-in-95">
                        <div className="relative">
                            <div className="absolute inset-0 bg-yellow-400 blur-2xl opacity-20 rounded-full animate-pulse"></div>
                            <div className="relative p-6 bg-white dark:bg-slate-800 rounded-2xl shadow-xl border-2 border-yellow-400 flex flex-col items-center gap-4">
                                <Shield className="w-16 h-16 text-yellow-500 animate-bounce" />
                                <div className="text-center">
                                    <h3 className="text-2xl font-bold text-slate-800 dark:text-slate-100">{t('speechManager.waitingForPolice')}</h3>
                                    <p className="text-slate-500">{t('speechManager.waitingForPoliceSub')}</p>
                                </div>
                            </div>
                        </div>

                        {!readonly && (
                            <div className="flex flex-col gap-3 w-full max-w-xs">
                                <div className="text-xs text-center text-slate-400 uppercase font-bold tracking-wider mb-2">{t('speechManager.judgeOverride')}</div>
                                <button
                                    onClick={() => handleSetOrder('UP')}
                                    className="flex items-center justify-center gap-2 px-6 py-3 bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200 rounded-lg transition-colors border border-slate-200 dark:border-slate-600"
                                >
                                    <ArrowUp className="w-5 h-5" />
                                    {t('speechManager.forceUp')}
                                </button>
                                <button
                                    onClick={() => handleSetOrder('DOWN')}
                                    className="flex items-center justify-center gap-2 px-6 py-3 bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200 rounded-lg transition-colors border border-slate-200 dark:border-slate-600"
                                >
                                    <ArrowDown className="w-5 h-5" />
                                    {t('speechManager.forceDown')}
                                </button>
                            </div>
                        )}
                    </div>
                ) : ( // ... rest of the speech UI
                    <>
                        {/* Current Speaker Node */}
                        {/* Speaker Area with Swiping Animation */}
                        <div className="relative w-full max-w-md min-h-[350px]">
                            {exitingSpeaker && (
                                <div key={exitingSpeaker.id} className="absolute inset-0 w-full animate-swipe-out z-0">
                                    <SpeakerCard
                                        player={exitingSpeaker}
                                        timeLeft={0}
                                        t={t}
                                        readonly={true} // Old speaker shouldn't be interactive during exit
                                    />
                                </div>
                            )}

                            {renderedSpeaker ? (
                                <div
                                    key={renderedSpeaker.id}
                                    className={`relative w-full z-10 ${speakerAnimation}`}
                                >
                                    <SpeakerCard
                                        player={renderedSpeaker}
                                        timeLeft={timeLeft}
                                        t={t}
                                        readonly={readonly}
                                        onSkip={handleSkip}
                                        onInterrupt={handleInterrupt}
                                    />
                                </div>
                            ) : (
                                !exitingSpeaker && <div className="text-slate-500 text-center mt-20">{t('speechManager.preparing')}</div>
                            )}
                        </div>

                        {/* Interrupt Votes */}
                        {speech?.interruptVotes && speech.interruptVotes.length > 0 && (
                            <div className="w-full max-w-md bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-900/30 rounded-lg p-4 animate-in slide-in-from-bottom-2 duration-300">
                                <h4 className="text-sm font-bold text-red-700 dark:text-red-400 mb-2 flex items-center gap-2">
                                    <Square className="w-4 h-4 animate-pulse" />
                                    {t('speechManager.interruptVote')} ({speech.interruptVotes.length} / {Math.floor(players.filter(p => p.isAlive).length / 2) + 1})
                                </h4>
                                <div className="flex flex-wrap gap-2">
                                    {speech.interruptVotes.map(voterId => {
                                        const voter = players.find(p => String(p.userId) === String(voterId));
                                        return (
                                            <div key={voterId} className="animate-in zoom-in-50 fade-in duration-300 flex items-center gap-1 bg-white dark:bg-slate-800 px-2 py-1 rounded text-xs border border-red-100 dark:border-red-900/50 shadow-sm">
                                                {voter?.avatar && <img src={voter.avatar} className="w-4 h-4 rounded-full" />}
                                                <span className="text-slate-700 dark:text-slate-300">{voter?.name || voterId}</span>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* Queue */}
                        <div className="flex flex-col gap-2 w-full max-w-sm items-center">
                            {nextPlayers && nextPlayers.map((player, idx) => (
                                <div key={player.id} className="relative w-full">
                                    {idx > 0 && (
                                        <div className="absolute left-1/2 top-0 transform -translate-x-1/2 -translate-y-full h-2 w-0.5 bg-slate-300 dark:bg-slate-700"></div>
                                    )}
                                    <div className="w-full bg-slate-50 dark:bg-slate-900 p-3 rounded-lg border border-slate-200 dark:border-slate-700 flex items-center justify-between opacity-70">
                                        <div className="flex items-center gap-3">
                                            <span className="w-6 h-6 flex items-center justify-center bg-slate-200 dark:bg-slate-800 rounded-full text-xs font-bold text-slate-500">
                                                {idx + 1}
                                            </span>
                                            <div className="flex items-center gap-2">
                                                <img src={player.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png'} className="w-8 h-8 rounded-full" />
                                                <span className="font-medium text-slate-700 dark:text-slate-300">{player.name}</span>
                                            </div>
                                        </div>
                                        <span className="text-xs text-slate-400">{t('speechManager.waiting')}</span>
                                    </div>
                                </div>
                            ))}
                        </div>

                        {nextPlayers && nextPlayers.length === 0 && currentSpeaker && (
                            <div className="text-slate-400 italic text-sm mt-4">{t('speechManager.noMoreSpeakers')}</div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
};

interface SpeakerCardProps {
    player: Player;
    timeLeft: number;
    t: any;
    readonly: boolean;
    onSkip?: () => void;
    onInterrupt?: () => void;
}

const SpeakerCard = ({ player, timeLeft, t, readonly, onSkip, onInterrupt }: SpeakerCardProps) => (
    <div className="relative">
        <div className="absolute inset-0 bg-indigo-500 blur-xl opacity-20 rounded-full animate-pulse"></div>
        <div className="relative bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-xl border-2 border-indigo-500 flex flex-col items-center gap-4 text-center">
            <div className="relative">
                <img src={player.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png'} alt={player.name} className="w-24 h-24 rounded-full border-4 border-indigo-100 dark:border-indigo-900 shadow-sm transition-transform duration-700 hover:rotate-6" />
                <div className="absolute -bottom-2 -right-2 bg-indigo-600 text-white p-2 rounded-full shadow-lg animate-bounce">
                    <Mic className="w-5 h-5 animate-pulse" />
                </div>
            </div>

            <div>
                <h2 className="text-2xl font-bold text-slate-900 dark:text-white">{player.name}</h2>
                <span className="text-indigo-500 font-medium">{t('speechManager.speaking')}</span>
            </div>

            <div className="flex items-center gap-2 text-3xl font-mono font-bold text-slate-700 dark:text-slate-300 bg-slate-100 dark:bg-slate-900 px-4 py-2 rounded-lg border border-slate-200 dark:border-slate-700">
                <Clock className="w-6 h-6 text-slate-400" />
                <span className={timeLeft < 10 ? 'text-red-500' : ''}>
                    {Math.floor(timeLeft / 60).toString().padStart(2, '0')}:{String(timeLeft % 60).padStart(2, '0')}
                </span>
            </div>

            {!readonly && (
                <div className="flex gap-2 w-full mt-4">
                    <button
                        onClick={onSkip}
                        className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300 rounded-lg hover:bg-amber-200 dark:hover:bg-amber-900/50 transition-colors"
                        title={t('tooltips.skipSpeaker')}
                    >
                        <SkipForward className="w-4 h-4" />
                        {t('speechManager.skip')}
                    </button>
                    <button
                        onClick={onInterrupt}
                        className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300 rounded-lg hover:bg-red-200 dark:hover:bg-red-900/50 transition-colors"
                        title={t('tooltips.interruptSpeech')}
                    >
                        <Square className="w-4 h-4 fill-current" />
                        {t('speechManager.interrupt')}
                    </button>
                </div>
            )}
        </div>
    </div>
);
