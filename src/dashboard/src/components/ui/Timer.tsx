import React, {useEffect, useState} from 'react';
import {Clock} from 'lucide-react';

interface TimerProps {
    /** End timestamp in milliseconds */
    endTime: number;
    /** Seconds threshold for warning state (color change/animation) */
    warnTime?: number;
    /** Whether to show the icon */
    showIcon?: boolean;
    /** Optional label to display above the timer */
    label?: string;
    /** Custom class for the wrapper */
    className?: string;
    /** Size preset for the timer text */
    size?: 'sm' | 'md' | 'lg';
    /** Whether the timer is currently paused */
    isPaused?: boolean;
}

export const Timer: React.FC<TimerProps> = ({
                                                endTime,
                                                warnTime = 10,
                                                showIcon = true,
                                                label,
                                                className = '',
                                                size = 'md',
                                                isPaused = false
                                            }) => {
    const [timeLeft, setTimeLeft] = useState<number>(0);

    const isWarning = timeLeft <= warnTime && timeLeft > 0;

    useEffect(() => {
        if (!endTime || isPaused) {
            if (!isPaused) setTimeLeft(0);
            return;
        }

        const tick = () => {
            const now = Date.now();
            const remaining = Math.max(0, Math.ceil((endTime - now) / 1000));
            setTimeLeft(remaining);
        };

        tick();
        const timer = setInterval(tick, 100);
        return () => clearInterval(timer);
    }, [endTime, isPaused]);

    const formatTime = (seconds: number) => {
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    };

    const sizeClasses = {
        sm: 'text-lg',
        md: 'text-2xl',
        lg: 'text-4xl'
    };

    return (
        <div className={`flex items-center gap-3 px-6 py-3 rounded-2xl border transition-all duration-500 shadow-sm
            ${isWarning
            ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-900/50 animate-pulse'
            : 'bg-white dark:bg-slate-900 border-slate-200 dark:border-white/10'} 
            ${className}`}>

            {showIcon && (
                <div className={`p-2 rounded-xl transition-colors duration-500 
                    ${isWarning
                    ? 'bg-red-500 text-white'
                    : 'bg-slate-100 dark:bg-white/5 text-slate-500 dark:text-slate-400'}`}>
                    <Clock className={`w-5 h-5 ${isWarning ? 'animate-spin-slow' : ''}`}/>
                </div>
            )}

            <div className="flex flex-col min-w-[80px]">
                {label && (
                    <span
                        className="text-[10px] font-black uppercase tracking-widest text-slate-400 dark:text-slate-500 leading-none mb-1">
                        {label}
                    </span>
                )}
                <span className={`font-mono font-black tabular-nums transition-colors duration-500 leading-none
                    ${sizeClasses[size]}
                    ${isWarning ? 'text-red-600 dark:text-red-400' : 'text-slate-900 dark:text-white'}`}>
                    {formatTime(timeLeft)}
                </span>
            </div>
        </div>
    );
};
