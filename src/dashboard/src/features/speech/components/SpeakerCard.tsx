import {Clock, Mic, SkipForward, Square} from 'lucide-react';
import {Player} from '@/types';
import {DiscordAvatar, DiscordName} from '@/components/DiscordUser';

interface SpeakerCardProps {
    player: Player;
    timeLeft: number;
    t: any;
    readonly: boolean;
    onSkip?: () => void;
    onInterrupt?: () => void;
}

export const SpeakerCard = ({player, timeLeft, t, readonly, onSkip, onInterrupt}: SpeakerCardProps) => (
    <div className="relative">
        <div className="absolute inset-0 bg-indigo-500 blur-xl opacity-20 rounded-full animate-pulse"></div>
        <div
            className="relative bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-xl border-2 border-indigo-500 flex flex-col items-center gap-4 text-center">
            <>
                <div className="relative">
                    <DiscordAvatar userId={player.userId}
                                   avatarClassName="w-24 h-24 rounded-full border-4 border-indigo-100 dark:border-indigo-900 shadow-sm transition-transform duration-700 hover:rotate-6"/>
                    <div
                        className="absolute -bottom-2 -right-2 bg-indigo-600 text-white p-2 rounded-full shadow-lg animate-bounce">
                        <Mic className="w-5 h-5 animate-pulse"/>
                    </div>
                </div>

                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-white">
                        <DiscordName userId={player.userId} fallbackName={player.name}/>
                    </h2>
                    <span className="text-indigo-500 font-medium">{t('speechManager.speaking')}</span>
                </div>
            </>

            <div
                className="flex items-center gap-2 text-3xl font-mono font-bold text-slate-700 dark:text-slate-300 bg-slate-100 dark:bg-slate-900 px-4 py-2 rounded-lg border border-slate-200 dark:border-slate-700">
                <Clock className="w-6 h-6 text-slate-400"/>
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
                        <SkipForward className="w-4 h-4"/>
                        {t('speechManager.skip')}
                    </button>
                    <button
                        onClick={onInterrupt}
                        className="flex-1 flex items-center justify-center gap-2 px-4 py-2 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300 rounded-lg hover:bg-red-200 dark:hover:bg-red-900/50 transition-colors"
                        title={t('tooltips.interruptSpeech')}
                    >
                        <Square className="w-4 h-4 fill-current"/>
                        {t('speechManager.interrupt')}
                    </button>
                </div>
            )}
        </div>
    </div>
);
