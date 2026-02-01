import {useState} from 'react';
import {AlertCircle, Check, Wifi, X} from 'lucide-react';
import {useTranslation} from '../lib/i18n';
import {api} from '../lib/api';

interface SettingsModalProps {
    onClose: () => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({onClose}) => {
    const {t} = useTranslation();
    const [backendUrl, setBackendUrl] = useState(api.getConfiguredUrl());
    const [testing, setTesting] = useState(false);
    const [testResult, setTestResult] = useState<'success' | 'error' | null>(null);

    const handleSave = async () => {
        api.setBackendUrl(backendUrl);
        window.location.reload(); // Reload to reconnect WebSocket and apply changes
    };

    const handleTest = async () => {
        setTesting(true);
        setTestResult(null);

        try {
            const tempApi = new (api.constructor as any)();
            tempApi.setBackendUrl(backendUrl);
            const success = await tempApi.testConnection();
            setTestResult(success ? 'success' : 'error');
        } catch {
            setTestResult('error');
        } finally {
            setTesting(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
            <div
                className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-300 dark:border-slate-700 shadow-2xl w-full max-w-md">
                {/* Header */}
                <div className="flex items-center justify-between p-6 border-b border-slate-300 dark:border-slate-800">
                    <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">{t('sidebar.gameSettings')}</h2>
                    <button
                        onClick={onClose}
                        className="p-2 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-lg transition-colors"
                    >
                        <X className="w-5 h-5 text-slate-600 dark:text-slate-400"/>
                    </button>
                </div>

                {/* Content */}
                <div className="p-6 space-y-6">


                    {/* Backend URL */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
                            {t('settingsModal.backendUrl')}
                        </label>
                        <input
                            type="text"
                            value={backendUrl}
                            onChange={(e) => setBackendUrl(e.target.value)}
                            placeholder={t('settingsModal.urlPlaceholder')}
                            className="w-full px-4 py-2 bg-slate-100 dark:bg-slate-950 border border-slate-300 dark:border-slate-700 rounded-lg text-slate-900 dark:text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        />
                        <p className="mt-2 text-xs text-slate-500 dark:text-slate-400">
                            {t('settingsModal.urlHint')}
                        </p>
                    </div>

                    {/* Test Connection */}
                    <div className="flex items-center gap-2">
                        <button
                            onClick={handleTest}
                            disabled={testing || !backendUrl}
                            className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:bg-slate-400 dark:disabled:bg-slate-700 text-white rounded-lg transition-colors disabled:cursor-not-allowed"
                        >
                            <Wifi className="w-4 h-4"/>
                            {testing ? t('settingsModal.testing') : t('settingsModal.testConnection')}
                        </button>

                        {testResult === 'success' && (
                            <div className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
                                <Check className="w-4 h-4"/>
                                <span className="text-sm">{t('settingsModal.connectionSuccess')}</span>
                            </div>
                        )}

                        {testResult === 'error' && (
                            <div className="flex items-center gap-1 text-red-600 dark:text-red-400">
                                <AlertCircle className="w-4 h-4"/>
                                <span className="text-sm">{t('settingsModal.connectionFailed')}</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Footer */}
                <div
                    className="flex items-center justify-end gap-3 p-6 border-t border-slate-300 dark:border-slate-800">
                    <button
                        onClick={onClose}
                        className="px-4 py-2 text-slate-700 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-800 rounded-lg transition-colors"
                    >
                        {t('common.cancel')}
                    </button>
                    <button
                        onClick={handleSave}
                        className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg transition-colors"
                    >
                        {t('common.save')}
                    </button>
                </div>
            </div>
        </div>
    );
};
