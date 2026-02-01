import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useTranslation } from '../lib/i18n';

export const AuthCallback = () => {
    const navigate = useNavigate();
    const { checkAuth } = useAuth();
    const { t } = useTranslation();

    useEffect(() => {
        const handleCallback = async () => {
            // Wait for a moment to let cookies settle
            await new Promise(resolve => setTimeout(resolve, 500));

            // Re-check auth to get the new session
            await checkAuth();

            // Get the guild ID from the URL parameters
            const params = new URLSearchParams(window.location.search);
            const guildId = params.get('guild_id');

            if (guildId) {
                // Redirect to the server dashboard
                navigate(`/server/${guildId}`);
            } else {
                // If no guild ID, redirect to server selector
                navigate('/');
            }
        };

        handleCallback();
    }, [navigate, checkAuth]);

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex items-center justify-center">
            <div className="text-center">
                <Loader2 className="w-12 h-12 text-indigo-500 animate-spin mx-auto mb-4" />
                <p className="text-slate-600 dark:text-slate-400">{t('auth.loggingIn')}</p>
            </div>
        </div>
    );
};
