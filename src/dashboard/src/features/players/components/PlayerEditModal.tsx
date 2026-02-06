import React, {useState} from 'react';
import {useTranslation} from '@/lib/i18n';
import {Player} from '@/types';
import {ChevronRight, Shield, Users, X} from 'lucide-react';
import {api} from '@/lib/api';

interface PlayerEditModalProps {
    player: Player;
    allPlayers: Player[];
    guildId: string;
    onClose: () => void;
    doubleIdentities?: boolean;
    availableRoles: string[];
}

export const PlayerEditModal: React.FC<PlayerEditModalProps> = ({
                                                                    player,
                                                                    allPlayers,
                                                                    guildId,
                                                                    onClose,
                                                                    doubleIdentities,
                                                                    availableRoles
                                                                }) => {
    const {t} = useTranslation();
    const [selectedRole1, setSelectedRole1] = useState<string>(player.roles[0] || '');
    const [selectedRole2, setSelectedRole2] = useState<string>(player.roles[1] || 'None');
    const [rolePositionLocked, setRolePositionLocked] = useState<boolean>(player.rolePositionLocked || false);

    // Default to session setting if available, otherwise fallback to player data
    const isDoubleIdentity = doubleIdentities !== undefined ? doubleIdentities : player.roles.length > 1;

    const [transferTarget, setTransferTarget] = useState<number | ''>('');
    const [loading, setLoading] = useState(false);

    // Filter alive players excluding current player for transfer
    const potentialSheriffs = allPlayers.filter(p => p.isAlive && p.id !== player.id);

    // Use passed availableRoles, filter out duplicates and sort
    const sortedRoles = Array.from(new Set(availableRoles)).sort();
    // Ensure "None" isn't in the primary list if possible, or handle it in specific selects

    const handleUpdateRoles = async () => {
        setLoading(true);
        try {
            const newRoles = [selectedRole1];
            if (isDoubleIdentity && selectedRole2 !== 'None' && selectedRole2 !== '') {
                newRoles.push(selectedRole2);
            }
            // Filter empty
            const finalRoles = newRoles.filter(r => r);

            // Update roles
            await api.updatePlayerRoles(guildId, player.id, finalRoles);

            // Update lock status if double identity
            if (isDoubleIdentity) {
                await api.setPlayerRoleLock(guildId, player.id, rolePositionLocked);
            }

            onClose();
        } catch (error) {
            console.error('Failed to update roles:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleTransferPolice = async () => {
        if (transferTarget === '') return;
        setLoading(true);
        try {
            await api.setPolice(guildId, transferTarget); // Set new sheriff
            onClose();
        } catch (error) {
            console.error('Failed to transfer police:', error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm animate-fade-in">
            <div
                className="bg-white dark:bg-slate-900 rounded-2xl shadow-xl w-full max-w-md overflow-hidden animate-scale-up border border-slate-200 dark:border-slate-800">
                <div
                    className="p-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between bg-slate-50/50 dark:bg-slate-800/50">
                    <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                        <span className="text-xl">{player.avatar ?
                            <img src={player.avatar} className="w-6 h-6 rounded-full inline-block"/> : '👤'}</span>
                        {t('players.edit')} - {player.name}
                    </h2>
                    <button onClick={onClose}
                            className="p-1 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors">
                        <X className="w-5 h-5 text-slate-500"/>
                    </button>
                </div>

                <div className="p-6 space-y-6">
                    {/* Role Editing Section */}
                    <div className="space-y-3">
                        <div className="flex items-center justify-between mb-4">
                            <div
                                className="flex items-center gap-2 text-indigo-600 dark:text-indigo-400 font-bold text-sm uppercase tracking-wider">
                                <Users className="w-4 h-4"/>
                                {t('roles.title' as any)}
                            </div>
                        </div>

                        <div
                            className="bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 rounded-lg p-4 space-y-3">
                            <div>
                                <label
                                    className="block text-xs font-semibold text-slate-500 mb-1">{t('roles.role' as any)} 1</label>
                                <select
                                    className="w-full bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                    value={selectedRole1}
                                    onChange={(e) => setSelectedRole1(e.target.value)}
                                >
                                    <option value="" disabled>{t('roles.unknown')}</option>
                                    {sortedRoles.map(role => (
                                        <option key={role} value={role}>{role}</option>
                                    ))}
                                </select>
                            </div>

                            {isDoubleIdentity && (
                                <div>
                                    <label
                                        className="block text-xs font-semibold text-slate-500 mb-1">{t('roles.role' as any)} 2</label>
                                    <select
                                        className="w-full bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                        value={selectedRole2}
                                        onChange={(e) => setSelectedRole2(e.target.value)}
                                    >
                                        <option value="None">{t('common.none', 'None')}</option>
                                        {sortedRoles.map(role => (
                                            <option key={role} value={role}>{role}</option>
                                        ))}
                                    </select>
                                </div>
                            )}

                            {isDoubleIdentity && (
                                <div className="pt-2 border-t border-slate-200 dark:border-slate-700 mt-2">
                                    <div className="flex items-center justify-between">
                                        <label
                                            className="text-xs font-semibold text-slate-500">{t('messages.lockRoleOrder')}</label>
                                        <label className="relative inline-flex items-center cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={rolePositionLocked}
                                                onChange={(e) => setRolePositionLocked(e.target.checked)}
                                                className="sr-only peer"
                                            />
                                            <div
                                                className="w-9 h-5 bg-slate-200 peer-focus:outline-none rounded-full peer dark:bg-slate-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all dark:border-gray-600 peer-checked:bg-indigo-600"></div>
                                        </label>
                                    </div>
                                </div>
                            )}

                            <button
                                onClick={handleUpdateRoles}
                                disabled={loading}
                                className="w-full mt-2 bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-bold shadow-sm disabled:opacity-50 transition-all"
                            >
                                {loading ? '...' : t('common.save')}
                            </button>
                        </div>
                    </div>

                    {/* Police Badge Transfer Section */}
                    {player.isSheriff ? (
                        <div className="space-y-3 pt-3 border-t border-slate-200 dark:border-slate-800">
                            <div
                                className="flex items-center gap-2 text-green-600 dark:text-green-500 font-bold text-sm uppercase tracking-wider">
                                <Shield className="w-4 h-4"/>
                                {t('status.sheriff')}
                            </div>
                            <div
                                className="bg-green-50 dark:bg-green-900/10 border border-green-200 dark:border-green-900/30 rounded-lg p-4">
                                <p className="text-sm text-slate-600 dark:text-slate-400 mb-3">
                                    {t('players.transferPoliceDescription', 'Transfer the police badge to another alive player.')}
                                </p>

                                <div className="flex gap-2">
                                    <select
                                        className="flex-1 bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                        value={transferTarget}
                                        onChange={(e) => setTransferTarget(e.target.value === '' ? '' : Number(e.target.value))}
                                    >
                                        <option value="">{t('players.selectTarget', 'Select Target...')}</option>
                                        {potentialSheriffs.map(p => (
                                            <option key={p.id} value={p.id}>
                                                {p.name}
                                            </option>
                                        ))}
                                    </select>
                                    <button
                                        disabled={!transferTarget || loading}
                                        onClick={handleTransferPolice}
                                        className="bg-green-600 hover:bg-green-700 text-white px-4 py-2 rounded-lg text-sm font-bold shadow-sm disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                                    >
                                        {loading ? '...' : <ChevronRight className="w-4 h-4"/>}
                                    </button>
                                </div>
                            </div>
                        </div>
                    ) : null}
                </div>
            </div>
        </div>
    );
};
