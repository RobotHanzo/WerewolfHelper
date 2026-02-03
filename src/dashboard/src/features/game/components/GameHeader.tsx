import {Link} from 'react-router-dom';
import {Mic, Moon, Pause, Play, SkipForward, Sun} from 'lucide-react';
import {GamePhase, Player, SpeechState} from '@/types';
import {useTranslation} from '@/lib/i18n';

interface GameHeaderProps {
    phase: GamePhase;
    dayCount: number;
    timerSeconds: number;
    onGlobalAction: (action: string) => void;
    speech?: SpeechState;
    players?: Player[];
    readonly?: boolean;
    currentStep?: string;
    currentState?: string;
}

export const GameHeader: React.FC<GameHeaderProps> = ({
                                                          phase,
                                                          dayCount,
                                                          timerSeconds,
                                                          onGlobalAction,
                                                          speech,
                                                          players,
                                                          readonly = false,
                                                          currentStep,
                                                          currentState
                                                      }) => {
    const {t} = useTranslation();
    const btnStyle = "px-4 py-2 rounded-lg font-medium transition-all active:scale-95 flex items-center justify-center gap-2";
    const btnPrimary = "bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-900/20 dark:shadow-indigo-900/20";
    const btnSecondary = "bg-slate-300 dark:bg-slate-700 hover:bg-slate-400 dark:hover:bg-slate-600 text-slate-800 dark:text-slate-200";

    const currentSpeaker = speech?.currentSpeakerId && players
        ? players.find(p => p.id === speech.currentSpeakerId)
        : null;

    return (
        <header
            className="h-16 bg-white/50 dark:bg-slate-900/50 backdrop-blur-md border-b border-slate-300 dark:border-slate-800 flex items-center justify-between px-6 z-10">
            <div className="flex items-center gap-6">
                <div className="flex flex-col">
                    <span
                        className="text-xs text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider">{t('gameHeader.currentPhase')}</span>
                    <div className="flex items-center gap-2 text-slate-900 dark:text-slate-100">
                        {phase === 'DAY' ? <Sun className="w-5 h-5 text-orange-500"/> :
                            <Moon className="w-5 h-5 text-indigo-500 dark:text-indigo-400"/>}
                        <span className="font-bold text-lg">
                            {currentStep ? currentStep : t(`phases.${phase}`)} {dayCount > 0 && `#${dayCount}`}
                        </span>
                    </div>
                </div>

                {currentSpeaker && (
                    <>
                        <div className="h-8 w-px bg-slate-300 dark:bg-slate-700"/>
                        <Link to="speech"
                              className="flex flex-col group cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-800/50 px-2 -mx-2 rounded transition-colors">
                            <span
                                className="text-xs text-indigo-500 font-bold uppercase tracking-wider flex items-center gap-1">
                                <Mic className="w-3 h-3 animate-pulse"/> {t('messages.speaking')}
                            </span>
                            <div className="flex items-center gap-2 text-slate-900 dark:text-slate-100 font-bold">
                                <span>{currentSpeaker.name}</span>
                                {speech?.endTime && (
                                    <span
                                        className="text-sm font-mono text-slate-500 bg-slate-100 dark:bg-slate-800 px-1 rounded border border-slate-200 dark:border-slate-700">
                                        {(() => {
                                            const seconds = Math.max(0, Math.ceil((speech.endTime - Date.now()) / 1000));
                                            return `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
                                        })()}
                                    </span>
                                )}
                            </div>
                        </Link>
                    </>
                )}

                <div className="h-8 w-px bg-slate-300 dark:bg-slate-700"/>

                <div className="flex flex-col">
                    <span
                        className="text-xs text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider">{t('gameHeader.timer')}</span>
                    <div
                        className={`font-mono font-bold text-xl ${timerSeconds < 10 ? 'text-red-500 dark:text-red-400' : 'text-slate-800 dark:text-slate-200'}`}>
                        {Math.floor(timerSeconds / 60)}:{String(timerSeconds % 60).padStart(2, '0')}
                    </div>
                </div>
            </div>

            <div className="flex items-center gap-3">
                {!readonly && (
                    phase === 'LOBBY' ? (
                        <button
                            onClick={() => onGlobalAction('start_game')}
                            className={`${btnStyle} ${btnPrimary}`}
                        >
                            <Play className="w-4 h-4"/> {t('gameHeader.startGame')}
                        </button>
                    ) : (
                        <>
                            <button onClick={() => onGlobalAction('pause')} className={`${btnStyle} ${btnSecondary}`}>
                                <Pause className="w-4 h-4"/>
                            </button>
                            {currentState === 'SPEECH_PHASE' ? (
                                <Link
                                    to="speech"
                                    className={`${btnStyle} ${btnSecondary} hover:bg-indigo-100 dark:hover:bg-indigo-900/50`}
                                >
                                    <Mic className="w-4 h-4"/> {t('gameHeader.manageSpeech') || "Manage Speech"}
                                </Link>
                            ) : (
                                <button
                                    onClick={() => onGlobalAction('next_phase')}
                                    className={`${btnStyle} ${btnSecondary}`}
                                >
                                    <SkipForward className="w-4 h-4"/> {t('gameHeader.nextPhase')}
                                </button>
                            )}
                        </>
                    )
                )}
            </div>
        </header>
    );
};
