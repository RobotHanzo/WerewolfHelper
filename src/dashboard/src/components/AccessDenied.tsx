import React from 'react';
import { ShieldAlert, ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from '../lib/i18n';

export const AccessDenied: React.FC = () => {
    const { t } = useTranslation();
    const navigate = useNavigate();

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col items-center justify-center p-6 text-center">
            <div className="max-w-md w-full bg-white dark:bg-slate-900 rounded-2xl shadow-xl border border-slate-200 dark:border-slate-800 p-8 space-y-6">
                <div className="flex justify-center">
                    <div className="p-4 bg-red-100 dark:bg-red-900/30 rounded-full">
                        <ShieldAlert className="w-16 h-16 text-red-600 dark:text-red-500" />
                    </div>
                </div>

                <div className="space-y-2">
                    <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
                        {t('accessDenied.title')}
                    </h1>
                    <p className="text-slate-600 dark:text-slate-400">
                        {t('accessDenied.message')}
                    </p>
                </div>

                <div className="p-4 bg-slate-50 dark:bg-slate-800/50 rounded-xl border border-slate-100 dark:border-slate-700 text-sm italic text-slate-500 dark:text-slate-400">
                    {t('accessDenied.suggestion')}
                </div>

                <button
                    onClick={() => navigate('/')}
                    className="w-full flex items-center justify-center gap-2 px-6 py-3 bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-200 rounded-lg transition-colors font-medium"
                >
                    <ArrowLeft className="w-4 h-4" />
                    {t('accessDenied.back')}
                </button>
            </div>
        </div>
    );
};
