import React, {useEffect, useRef, useState} from 'react';
import {CheckCircle2, Loader2, XCircle} from 'lucide-react';
import {useTranslation} from '../lib/i18n';

interface ProgressOverlayProps {
    isVisible: boolean;
    title: string;
    logs: string[];
    onComplete?: () => void;
    autoCloseDelay?: number; // ms to wait before closing on success
    status: 'processing' | 'success' | 'error';
    error?: string;
    progress?: number;
    children?: React.ReactNode;
}

export const ProgressOverlay: React.FC<ProgressOverlayProps> = ({
                                                                    isVisible,
                                                                    title,
                                                                    logs,
                                                                    onComplete,
                                                                    autoCloseDelay = 1500,
                                                                    status,
                                                                    error,
                                                                    progress,
                                                                    children
                                                                }) => {
    const {t} = useTranslation();
    const logEndRef = useRef<HTMLDivElement>(null);
    const [isAnimating, setIsAnimating] = useState(false);
    const [shouldRender, setShouldRender] = useState(false);

    // Auto-scroll to bottom of logs
    useEffect(() => {
        logEndRef.current?.scrollIntoView({behavior: 'smooth'});
    }, [logs]);

    // Handle animation on mount/unmount
    useEffect(() => {
        if (isVisible) {
            setShouldRender(true);
            // Small delay to trigger animation
            requestAnimationFrame(() => {
                setIsAnimating(true);
            });
        } else {
            setIsAnimating(false);
            // Wait for animation to complete before unmounting
            const timer = setTimeout(() => {
                setShouldRender(false);
            }, 300); // Match animation duration
            return () => clearTimeout(timer);
        }
    }, [isVisible]);

    // Handle auto-close
    useEffect(() => {
        if (status === 'success' && onComplete) {
            const timer = setTimeout(() => {
                onComplete();
            }, autoCloseDelay);
            return () => clearTimeout(timer);
        }
    }, [status, onComplete, autoCloseDelay]);

    if (!shouldRender) return null;

    return (
        <div
            className={`fixed inset-0 z-50 flex items-center justify-center transition-all duration-300 ${isAnimating ? 'bg-slate-900/80 backdrop-blur-sm' : 'bg-slate-900/0'
            }`}>
            <div
                className={`bg-white dark:bg-slate-900 w-full max-w-lg rounded-2xl shadow-2xl overflow-hidden border border-slate-200 dark:border-slate-700 m-4 flex flex-col max-h-[80vh] transition-all duration-300 ${isAnimating ? 'opacity-100 scale-100' : 'opacity-0 scale-95'
                }`}>

                {/* Header */}
                <div
                    className="p-6 border-b border-slate-100 dark:border-slate-800 flex items-center gap-4 bg-slate-50 dark:bg-slate-950/50">
                    {status === 'processing' && <Loader2 className="w-8 h-8 text-indigo-500 animate-spin"/>}
                    {status === 'success' && <CheckCircle2 className="w-8 h-8 text-emerald-500"/>}
                    {status === 'error' && <XCircle className="w-8 h-8 text-red-500"/>}

                    <div className="flex-1">
                        <h2 className="text-xl font-bold text-slate-900 dark:text-white">
                            {status === 'error' ? t('progressOverlay.operationFailed') : title}
                        </h2>
                        {status === 'processing' && (
                            <div className="space-y-1">
                                <p className="text-sm text-slate-500 dark:text-slate-400 animate-pulse">{t('progressOverlay.processing')} {progress !== undefined ? `${Math.round(progress)}%` : ''}</p>
                                {progress !== undefined && (
                                    <div
                                        className="w-full bg-slate-200 dark:bg-slate-700 rounded-full h-1.5 overflow-hidden">
                                        <div
                                            className="bg-indigo-500 h-1.5 rounded-full transition-all duration-300 ease-out"
                                            style={{width: `${Math.max(0, Math.min(100, progress))}%`}}
                                        />
                                    </div>
                                )}
                            </div>
                        )}
                        {status === 'success' &&
                            <p className="text-sm text-emerald-600 dark:text-emerald-400">{t('progressOverlay.complete')}</p>}
                        {status === 'error' &&
                            <p className="text-sm text-red-600 dark:text-red-400">{error || t('progressOverlay.unknownError')}</p>}
                    </div>
                </div>

                {/* Content Area */}
                <div className="flex-1 overflow-y-auto bg-slate-950 relative min-h-[200px] flex flex-col">
                    {children ? (
                        <div className="flex-1 p-6">
                            {children}
                        </div>
                    ) : (
                        <div className="flex-1 p-4 font-mono text-sm">
                            <div className="space-y-2">
                                {logs.map((log, index) => (
                                    <div key={index} className="flex gap-3 text-slate-300">
                                        <span className="text-slate-600 select-none">{'>'}</span>
                                        <span className={
                                            log.includes('錯誤') || log.includes('Failed') ? 'text-red-400' :
                                                log.includes('完成') || log.includes('Success') ? 'text-emerald-400' :
                                                    'text-slate-300'
                                        }>
                                            {log}
                                        </span>
                                    </div>
                                ))}
                                {status === 'processing' && (
                                    <div className="flex gap-3 text-indigo-400 animate-pulse">
                                        <span className="text-slate-600 select-none">{'>'}</span>
                                        <span className="after:content-[''] after:animate-pulse">_</span>
                                    </div>
                                )}
                                <div ref={logEndRef}/>
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer - Show OK button for success or error */}
                {(status === 'success' || status === 'error') && (
                    <div
                        className="p-4 border-t border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-950/30 flex justify-end">
                        <button
                            onClick={onComplete}
                            className={`px-6 py-2 rounded-lg transition-colors font-medium ${status === 'success'
                                ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                                : 'bg-slate-200 dark:bg-slate-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200'
                            }`}
                        >
                            {t('progressOverlay.ok')}
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
};
