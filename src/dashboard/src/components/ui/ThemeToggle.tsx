import {Moon, Sun} from 'lucide-react';
import {useTheme} from '@/lib/ThemeProvider';
import {useTranslation} from '@/lib/i18n';

export const ThemeToggle: React.FC = () => {
    const {theme, toggleTheme} = useTheme();
    const {t} = useTranslation();

    return (
        <button
            onClick={toggleTheme}
            className="w-10 h-10 flex items-center justify-center rounded-lg bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 transition-all duration-200 active:scale-95"
            title={theme === 'dark' ? t('tooltips.switchToLight') : t('tooltips.switchToDark')}
        >
            {theme === 'dark' ? (
                <Sun className="w-5 h-5 text-yellow-500"/>
            ) : (
                <Moon className="w-5 h-5 text-indigo-600"/>
            )}
        </button>
    );
};
