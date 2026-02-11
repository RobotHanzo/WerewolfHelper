import { LogOut } from 'lucide-react';
import { useTranslation } from '@/lib/i18n';

interface SessionExpiredModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const SessionExpiredModal = ({ isOpen, onClose }: SessionExpiredModalProps) => {
  const { t } = useTranslation();

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-xl w-full max-w-sm overflow-hidden animate-in zoom-in-95 duration-200">
        <div className="p-6 flex flex-col items-center text-center">
          <div className="w-12 h-12 bg-red-100 dark:bg-red-900/30 rounded-full flex items-center justify-center mb-4">
            <LogOut className="w-6 h-6 text-red-600 dark:text-red-400" />
          </div>

          <h3 className="text-lg font-bold text-slate-900 dark:text-slate-100 mb-2">
            {t('auth.sessionExpiredTitle') || 'Session Expired'}
          </h3>

          <p className="text-slate-600 dark:text-slate-400 mb-6">
            {t('auth.sessionExpiredMessage') || 'Your session has expired. Please log in again.'}
          </p>

          <button
            onClick={onClose}
            className="w-full px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-medium rounded-lg transition-colors focus:ring-4 focus:ring-indigo-500/20 outline-none"
          >
            {t('auth.loginAgain') || 'Login Again'}
          </button>
        </div>
      </div>
    </div>
  );
};
