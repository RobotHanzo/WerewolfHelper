import React, {useState} from 'react';
import {Check, Search, X} from 'lucide-react';
import {useTranslation} from '@/lib/i18n';
import {Player} from '@/types';
import {DiscordAvatar, DiscordName} from '@/components/DiscordUser';

interface PlayerSelectModalProps {
    title: string;
    players: Player[];
    onSelect: (playerId: number | string) => void;
    onClose: () => void;
    filter?: (p: Player) => boolean;
}

export const PlayerSelectModal: React.FC<PlayerSelectModalProps> = ({title, players, onSelect, onClose, filter}) => {
    const {t} = useTranslation();
    const [search, setSearch] = useState('');

    const filteredPlayers = players
        .filter(p => filter ? filter(p) : true)
        .filter(p => p.name.toLowerCase().includes(search.toLowerCase()) || (p.userId && p.userId.includes(search)));

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-200">
            <div
                className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-md w-full border border-slate-200 dark:border-slate-800 overflow-hidden flex flex-col max-h-[80vh]">
                <div
                    className="flex items-center justify-between p-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50">
                    <h2 className="text-lg font-bold text-slate-900 dark:text-white">
                        {title}
                    </h2>
                    <button onClick={onClose}
                            className="text-slate-500 hover:text-slate-700 dark:hover:text-slate-300 transition-colors">
                        <X className="w-5 h-5"/>
                    </button>
                </div>

                <div className="p-4 border-b border-slate-200 dark:border-slate-800">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400"/>
                        <input
                            type="text"
                            placeholder={t('search.placeholder') || "Search players..."}
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            className="w-full pl-9 pr-4 py-2 bg-slate-100 dark:bg-slate-950 border border-slate-300 dark:border-slate-700 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        />
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto p-2 space-y-1">
                    {filteredPlayers.length === 0 ? (
                        <div className="text-center py-8 text-slate-500 text-sm">
                            {t('search.noResults')}
                        </div>
                    ) : (
                        filteredPlayers.map(p => (
                            <button
                                key={p.id}
                                onClick={() => {
                                    onSelect(p.id);
                                    onClose();
                                }}
                                className="w-full flex items-center gap-3 p-3 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors group text-left"
                            >
                                <div className="relative">
                                    <DiscordAvatar userId={p.userId}
                                                   avatarClassName="w-10 h-10 rounded-full bg-slate-200 dark:bg-slate-700"/>
                                    {/* Status indicators if needed, e.g. role icons */}
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div
                                        className="font-medium text-slate-900 dark:text-slate-200 truncate">
                                        <DiscordName userId={p.userId} fallbackName={p.name}/>
                                    </div>
                                    <div className="text-xs text-slate-500 truncate flex gap-1">
                                        {p.roles.map(r => <span key={r}
                                                                className="bg-slate-200 dark:bg-slate-800 px-1 rounded">{r}</span>)}
                                    </div>
                                </div>
                                <Check
                                    className="w-4 h-4 text-indigo-500 opacity-0 group-hover:opacity-100 transition-opacity"/>
                            </button>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
};
