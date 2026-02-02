import {useEffect} from 'react';
import {useNavigate} from 'react-router-dom';
import {useAuth} from '@/features/auth/contexts/AuthContext';
import {ServerSelector} from '../components/ServerSelector';

export const ServerSelectionPage = () => {
    const navigate = useNavigate();
    const {user, loading} = useAuth();

    useEffect(() => {
        if (!loading && !user) {
            navigate('/login');
        }
    }, [user, loading, navigate]);

    if (loading) {
        return (
            <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex items-center justify-center">
                <div className="text-slate-600 dark:text-slate-400">Loading...</div>
            </div>
        );
    }

    if (!user) return null;

    const handleSelectServer = (guildId: string) => {
        navigate(`/server/${guildId}`);
    };
    return <ServerSelector onSelectServer={handleSelectServer} onBack={() => navigate('/login')}/>;
};
