import React, {useState} from 'react';
import {Clock, Play, X} from 'lucide-react';
import {useTranslation} from '@/lib/i18n';

interface TimerControlModalProps {
    onClose: () => void;
    onStart: (seconds: number) => void;
}

export const TimerControlModal: React.FC<TimerControlModalProps> = ({onClose, onStart}) => {
    const {t} = useTranslation();
    const [minutes, setMinutes] = useState(0);
    const [seconds, setSeconds] = useState(60);

    const presets = [30, 60, 90, 180];

    const handleStart = () => {
        const totalSeconds = (minutes * 60) + seconds;
        if (totalSeconds > 0) {
            onStart(totalSeconds);
            onClose();
        }
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
            <div
                className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-md w-full border border-slate-200 dark:border-slate-800 overflow-hidden scale-in-95 animate-in zoom-in-95 duration-200">
                <div
                    className="flex items-center justify-between p-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50">
                    <h2 className="text-lg font-bold text-slate-900 dark:text-white flex items-center gap-2">
                        <Clock className="w-5 h-5 text-indigo-500"/>
                        {t('timer.title') || 'Start Timer'}
                    </h2>
                    <button onClick={onClose}
                            className="text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 transition-colors">
                        <X className="w-5 h-5"/>
                    </button>
                </div>

                <div className="p-6 space-y-6">
                    {/* Custom Input */}
                    <div className="flex items-end justify-center gap-4">
                        <div className="text-center">
                            <label
                                className="block text-xs font-semibold text-slate-500 uppercase mb-1">{t('timer.minutes')}</label>
                            <input
                                type="number"
                                min="0"
                                max="60"
                                value={minutes}
                                onChange={(e) => setMinutes(Math.max(0, parseInt(e.target.value) || 0))}
                                className="w-20 text-center text-2xl font-bold bg-slate-100 dark:bg-slate-950 border border-slate-300 dark:border-slate-700 rounded-lg py-2 focus:ring-2 focus:ring-indigo-500 outline-none"
                            />
                        </div>
                        <span className="text-2xl font-bold text-slate-400 pb-2">:</span>
                        <div className="text-center">
                            <label
                                className="block text-xs font-semibold text-slate-500 uppercase mb-1">{t('timer.seconds')}</label>
                            <input
                                type="number"
                                min="0"
                                max="59"
                                value={seconds}
                                onChange={(e) => setSeconds(Math.max(0, parseInt(e.target.value) || 0))}
                                className="w-20 text-center text-2xl font-bold bg-slate-100 dark:bg-slate-950 border border-slate-300 dark:border-slate-700 rounded-lg py-2 focus:ring-2 focus:ring-indigo-500 outline-none"
                            />
                        </div>
                    </div>

                    {/* Presets */}
                    <div className="grid grid-cols-4 gap-2">
                        {presets.map(s => (
                            <button
                                key={s}
                                onClick={() => {
                                    setMinutes(Math.floor(s / 60));
                                    setSeconds(s % 60);
                                }}
                                className="px-2 py-2 text-sm font-medium bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-md transition-colors"
                            >
                                {s >= 60 ? `${s / 60}m` : `${s}s`}
                            </button>
                        ))}
                    </div>

                    <button
                        onClick={handleStart}
                        className="w-full flex items-center justify-center gap-2 bg-indigo-600 hover:bg-indigo-700 text-white py-3 rounded-lg font-bold transition-all transform active:scale-95"
                    >
                        <Play className="w-5 h-5 fill-current"/>
                        {t('timer.start') || 'Start Timer'}
                    </button>
                </div>
            </div>
        </div>
    );
};
