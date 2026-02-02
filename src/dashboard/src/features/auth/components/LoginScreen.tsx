import {Moon} from 'lucide-react';
import {useTranslation} from '@/lib/i18n';

interface LoginScreenProps {
    onLogin: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({onLogin}) => {
    const {t} = useTranslation();

    return (
        <div
            className="min-h-screen bg-slate-50 dark:bg-slate-950 flex items-center justify-center p-4 relative overflow-hidden">
            {/* Background Effects */}
            <div className="absolute top-0 left-0 w-full h-full overflow-hidden pointer-events-none">
                <div
                    className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-indigo-900/20 dark:bg-indigo-900/20 rounded-full blur-[100px]"/>
                <div
                    className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-red-900/10 dark:bg-red-900/10 rounded-full blur-[100px]"/>
            </div>

            <div
                className="w-full max-w-md bg-white/80 dark:bg-slate-900/80 backdrop-blur-xl p-8 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-2xl z-10">
                <div className="flex flex-col items-center mb-8">
                    <div
                        className="w-20 h-20 bg-gradient-to-br from-indigo-500 to-purple-600 rounded-2xl flex items-center justify-center shadow-lg mb-4 transform rotate-3">
                        <Moon className="w-10 h-10 text-white"/>
                    </div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-white mb-2">{t('login.title')}</h1>
                    <p className="text-slate-600 dark:text-slate-400 text-center">{t('login.subtitle')}</p>
                </div>

                <div className="space-y-4">
                    <button
                        onClick={onLogin}
                        className="w-full bg-[#5865F2] hover:bg-[#4752C4] text-white font-semibold py-3 px-4 rounded-xl transition-all duration-200 flex items-center justify-center gap-3 shadow-lg shadow-[#5865F2]/20 group"
                    >
                        <svg className="w-5 h-5 fill-current" viewBox="0 0 127.14 96.36">
                            <path
                                d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c1.24-21.6-3.79-45.2-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z"/>
                        </svg>
                        {t('login.loginButton')}
                    </button>
                    <div className="text-center">
                        <span className="text-xs text-slate-500 dark:text-slate-500">{t('login.restriction')}</span>
                    </div>
                </div>
            </div>
        </div>
    );
};
