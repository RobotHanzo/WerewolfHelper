import React, {useState} from 'react';
import {useTranslation} from '@/lib/i18n';
import {Player} from '@/types';
import {Skull, X} from 'lucide-react';
import {api} from '@/lib/api';
import {DiscordUser} from '@/components/DiscordUser';

interface DeathConfirmModalProps {
    player: Player;
    guildId: string;
    onClose: () => void;
}

export const DeathConfirmModal: React.FC<DeathConfirmModalProps> = ({player, guildId, onClose}) => {
    const {t} = useTranslation();
    const [lastWords, setLastWords] = useState(false);
    const [loading, setLoading] = useState(false);

    const handleConfirm = async () => {
        setLoading(true);
        try {
            await api.markPlayerDead(guildId, player.id, lastWords);
            onClose();
        } catch (error) {
            console.error('Failed to mark player as dead:', error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in">
            <div
                className="bg-white dark:bg-slate-900 rounded-2xl shadow-xl w-full max-w-sm overflow-hidden animate-scale-up border border-slate-200 dark:border-slate-800">
                <div
                    className="p-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between bg-red-50 dark:bg-red-900/20">
                    <h2 className="text-lg font-bold text-red-700 dark:text-red-400 flex items-center gap-2">
                        <Skull className="w-5 h-5"/>
                        {t('actions.kill')}
                    </h2>
                    <button onClick={onClose} className="p-1 rounded-full hover:bg-black/5 transition-colors">
                        <X className="w-5 h-5 text-slate-500"/>
                    </button>
                </div>

                <div className="p-6 space-y-4">
                    <DiscordUser
                        userId={player.userId}
                        fallbackName={player.name}
                        avatarClassName="w-12 h-12 rounded-full"
                    >
                        {({name, avatarElement}) => (
                            <div className="flex items-center gap-3">
                                <div className="text-4xl">
                                    {avatarElement}
                                </div>
                                <div>
                                    <p className="font-bold text-lg dark:text-slate-200">{name}</p>
                                    <p className="text-sm text-slate-500 dark:text-slate-400">
                                        {player.roles.join(', ') || t('roles.unknown')}
                                    </p>
                                </div>
                            </div>
                        )}
                    </DiscordUser>

                    <div
                        className="bg-slate-50 dark:bg-slate-800/50 p-4 rounded-lg text-sm text-slate-600 dark:text-slate-300">
                        <p>{t('players.killConfirmation', 'Are you sure you want to kill this player?')}</p>
                    </div>

                    <div className="flex items-center gap-2 pt-2">
                        <input
                            type="checkbox"
                            id="lastWords"
                            checked={lastWords}
                            onChange={(e) => setLastWords(e.target.checked)}
                            className="w-4 h-4 text-red-600 rounded border-slate-300 focus:ring-red-500"
                        />
                        <label htmlFor="lastWords"
                               className="text-sm font-medium text-slate-700 dark:text-slate-300 select-none cursor-pointer">
                            {t('game.lastWords', 'Allow Last Words')}
                        </label>
                    </div>

                    <div className="flex gap-3 pt-4">
                        <button
                            onClick={onClose}
                            className="flex-1 px-4 py-2 bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 rounded-lg hover:bg-slate-200 dark:hover:bg-slate-700 font-medium transition-colors"
                        >
                            {t('common.cancel')}
                        </button>
                        <button
                            onClick={handleConfirm}
                            disabled={loading}
                            className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 font-bold shadow-sm disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
                        >
                            {loading ? '...' : (
                                <>
                                    <Skull className="w-4 h-4"/>
                                    {t('actions.kill')}
                                </>
                            )}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};
