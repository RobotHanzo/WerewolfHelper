import React from 'react';
import {Loader2, Minus, Plus} from 'lucide-react';

interface CounterProps {
    value: number;
    onIncrement: () => void;
    onDecrement: () => void;
    min?: number;
    max?: number;
    loading?: boolean;
    disabled?: boolean;
    variant?: 'default' | 'card';
    className?: string;
}

export const Counter: React.FC<CounterProps> = ({
                                                    value,
                                                    onIncrement,
                                                    onDecrement,
                                                    min = 0,
                                                    max = Infinity,
                                                    loading = false,
                                                    disabled = false,
                                                    variant = 'default',
                                                    className = ''
                                                }) => {
    const isCard = variant === 'card';

    if (isCard) {
        return (
            <div
                className={`flex items-center gap-4 bg-white dark:bg-slate-900 p-2 rounded-lg border border-slate-200 dark:border-slate-700 shadow-sm ${className}`}>
                <button
                    onClick={onDecrement}
                    disabled={disabled || loading || value <= min}
                    className="w-10 h-10 flex items-center justify-center text-slate-500 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-all disabled:opacity-30 disabled:cursor-not-allowed"
                >
                    <Minus className="w-6 h-6"/>
                </button>

                <div className="flex flex-col items-center min-w-[3rem]">
                    {loading ? (
                        <Loader2 className="w-6 h-6 animate-spin text-indigo-500"/>
                    ) : (
                        <span className="text-2xl font-black text-slate-900 dark:text-white tabular-nums">
                            {value}
                        </span>
                    )}
                </div>

                <button
                    onClick={onIncrement}
                    disabled={disabled || loading || value >= max}
                    className="w-10 h-10 flex items-center justify-center text-slate-500 hover:text-emerald-500 hover:bg-emerald-50 dark:hover:bg-emerald-900/20 rounded-lg transition-all disabled:opacity-30 disabled:cursor-not-allowed"
                >
                    <Plus className="w-6 h-6"/>
                </button>
            </div>
        );
    }

    return (
        <div className={`flex items-center gap-2 ${className}`}>
            <button
                onClick={onDecrement}
                disabled={disabled || loading || value <= min}
                className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
                <Minus className="w-4 h-4"/>
            </button>

            <div className="flex flex-col items-center min-w-[2rem]">
                {loading ? (
                    <Loader2 className="w-4 h-4 animate-spin text-indigo-500"/>
                ) : (
                    <span
                        className="w-8 text-center font-bold text-slate-900 dark:text-white bg-white dark:bg-slate-900 rounded py-0.5 border border-slate-200 dark:border-slate-700">
                        {value}
                    </span>
                )}
            </div>

            <button
                onClick={onIncrement}
                disabled={disabled || loading || value >= max}
                className="p-1 text-slate-400 hover:text-green-500 hover:bg-green-50 dark:hover:bg-green-900/20 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
                <Plus className="w-4 h-4"/>
            </button>
        </div>
    );
};
