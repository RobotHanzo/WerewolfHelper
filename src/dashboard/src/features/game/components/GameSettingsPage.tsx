import React, {useEffect, useRef, useState} from 'react';
import {AlertCircle, Check, Dices, Loader2, Minus, Plus, Users} from 'lucide-react';
import {useParams} from 'react-router-dom';
import {useTranslation} from '@/lib/i18n';
import {useMutation} from '@tanstack/react-query';
import {
    updateSettingsMutation,
    addRoleMutation,
    removeRoleMutation,
    assignRolesMutation,
    setPlayerCountMutation
} from '@/api/@tanstack/react-query.gen';
import {Toggle} from '@/components/ui/Toggle';
import {Counter} from '@/components/ui/Counter';
import {useAuth} from '@/features/auth/contexts/AuthContext';
import {useGameState} from '@/features/game/hooks/useGameState';

export const GameSettingsPage: React.FC = () => {
    const {guildId} = useParams<{ guildId: string }>();
    const {t} = useTranslation();
    const {user} = useAuth();

    const [saving, setSaving] = useState(false);
    const [justSaved, setJustSaved] = useState(false);
    const [muteAfterSpeech, setMuteAfterSpeech] = useState(true);
    const [doubleIdentities, setDoubleIdentities] = useState(false);
    const [roles, setRoles] = useState<string[]>([]);
    const [roleCounts, setRoleCounts] = useState<Record<string, number>>({});
    const [witchCanSaveSelf, setWitchCanSaveSelf] = useState(true);
    const [allowWolfSelfKill, setAllowWolfSelfKill] = useState(true);
    const [hiddenRoleOnDeath, setHiddenRoleOnDeath] = useState(false);

    // State for initial values (to track changes)
    const [initialPlayerCount, setInitialPlayerCount] = useState<number>(12);

    const [playerCount, setPlayerCount] = useState<number>(12);
    const [selectedRole, setSelectedRole] = useState<string>('');
    const [updatingRoles, setUpdatingRoles] = useState(false);
    const [updatingPlayerCount, setUpdatingPlayerCount] = useState(false);
    const [assigningRoles, setAssigningRoles] = useState(false);
    const [pendingFields, setPendingFields] = useState<Record<string, boolean>>({});
    const [error, setError] = useState<string | null>(null);

    const {gameState} = useGameState(guildId, user);
    const loading = !gameState;

    // Mutations
    const updateSettingsMut = useMutation(updateSettingsMutation());
    const addRoleMut = useMutation(addRoleMutation());
    const removeRoleMut = useMutation(removeRoleMutation());
    const assignRolesMut = useMutation(assignRolesMutation());
    const setPlayerCountMut = useMutation(setPlayerCountMutation());

    const AVAILABLE_ROLES = [
        "平民", "狼人", "女巫", "預言家", "獵人",
        "守衛", "白痴", "騎士", "守墓人", "攝夢人", "魔術師",
        "狼王", "白狼王", "狼兄", "狼弟", "隱狼", "石像鬼",
        "惡靈騎士", "血月使者", "機械狼", "複製人"
    ];

    const isFirstLoad = useRef(true);

    const playerCountChanged = playerCount !== initialPlayerCount;

    // Auto-save effect for toggles
    useEffect(() => {
        if (isFirstLoad.current || loading) return;

        const saveSettings = async () => {
            if (!guildId) return;
            setSaving(true);
            try {
                await updateSettingsMut.mutateAsync({
                    path: {guildId},
                    body: {
                        muteAfterSpeech,
                        doubleIdentities,
                        witchCanSaveSelf,
                        allowWolfSelfKill,
                        hiddenRoleOnDeath
                    } as any
                });
                setJustSaved(true);
                setTimeout(() => setJustSaved(false), 2000);
                setPendingFields({});
            } catch (e) {
                console.error("Failed to auto-save settings", e);
            } finally {
                setSaving(false);
            }
        };
        saveSettings();
    }, [muteAfterSpeech, doubleIdentities, witchCanSaveSelf, allowWolfSelfKill, hiddenRoleOnDeath, guildId]);

    // Load settings from queries
    useEffect(() => {
        if (!gameState) return;
        isFirstLoad.current = true;

        // Access data from wrapped response
        const session = gameState;
        const rolesArray = session?.roles;

        if (!session || !rolesArray) return;

        setMuteAfterSpeech(session.muteAfterSpeech);
        setDoubleIdentities(session.doubleIdentities);

        // Load special settings from session.settings if available, or defaults
        if (session.settings) {
            setWitchCanSaveSelf(session.settings.witchCanSaveSelf ?? true);
            setAllowWolfSelfKill(session.settings.allowWolfSelfKill ?? true);
            setHiddenRoleOnDeath(session.settings.hiddenRoleOnDeath ?? false);
        }

        // Set player count from current players length
        if (session.players) {
            const count = Array.isArray(session.players)
                ? session.players.length
                : Object.keys(session.players).length;
            setPlayerCount(count);
            setInitialPlayerCount(count);
        }

        setRoles(Array.isArray(rolesArray) ? rolesArray.filter((r: unknown): r is string => typeof r === 'string') : []);

        setTimeout(() => {
            isFirstLoad.current = false;
        }, 100);
    }, [gameState]);

    useEffect(() => {
        const counts: Record<string, number> = {};
        roles.forEach(role => {
            counts[role] = (counts[role] || 0) + 1;
        });
        setRoleCounts(counts);
    }, [roles]);

    const handleAddRole = async (role: string) => {
        if (!guildId || updatingRoles || !role.trim()) return;
        setUpdatingRoles(true);
        try {
            await addRoleMut.mutateAsync({
                path: {guildId},
                query: {role: role.trim(), amount: 1}
            });
            setSelectedRole('');
        } catch (e) {
            console.error("Failed to add role", e);
        } finally {
            setUpdatingRoles(false);
        }
    };

    const handleRemoveRole = async (role: string) => {
        if (!guildId || updatingRoles) return;
        setUpdatingRoles(true);
        try {
            await removeRoleMut.mutateAsync({
                path: {guildId, role},
                query: {amount: 1}
            });
        } catch (e) {
            console.error("Failed to remove role", e);
        } finally {
            setUpdatingRoles(false);
        }
    };

    const handleRandomAssign = async () => {
        if (!guildId || assigningRoles) return;
        setError(null);
        setAssigningRoles(true);
        try {
            await assignRolesMut.mutateAsync({path: {guildId}});
        } catch (error: any) {
            console.error("Assign failed", error);
            setError(error.message || t('errors.assignFailed'));
        } finally {
            setAssigningRoles(false);
        }
    };

    const handlePlayerCountUpdate = async () => {
        if (!guildId || updatingPlayerCount) return;
        setUpdatingPlayerCount(true);
        try {
            await setPlayerCountMut.mutateAsync({
                path: {guildId},
                body: {count: playerCount}
            });
            setInitialPlayerCount(playerCount);
        } catch (error: any) {
            console.error("Update failed", error);
        } finally {
            setUpdatingPlayerCount(false);
        }
    };

    const toggleMute = (checked: boolean) => {
        if (saving || pendingFields['mute']) return;
        setMuteAfterSpeech(checked);
        setPendingFields(prev => ({...prev, mute: true}));
    };

    const toggleDoubleIdentities = (checked: boolean) => {
        if (saving || pendingFields['double']) return;
        setDoubleIdentities(checked);
        setPendingFields(prev => ({...prev, double: true}));
    };

    const toggleWitchCanSaveSelf = (checked: boolean) => {
        if (saving || pendingFields['witchSave']) return;
        setWitchCanSaveSelf(checked);
        setPendingFields(prev => ({...prev, witchSave: true}));
    };

    const toggleAllowWolfSelfKill = (checked: boolean) => {
        if (saving || pendingFields['wolfSelfKill']) return;
        setAllowWolfSelfKill(checked);
        setPendingFields(prev => ({...prev, wolfSelfKill: true}));
    };

    const toggleHiddenRoleOnDeath = (checked: boolean) => {
        if (saving || pendingFields['hiddenRole']) return;
        setHiddenRoleOnDeath(checked);
        setPendingFields(prev => ({...prev, hiddenRole: true}));
    };

    if (loading) {
        return (
            <div className="flex justify-center items-center h-64">
                <Loader2 className="w-8 h-8 animate-spin text-indigo-600"/>
            </div>
        );
    }

    return (
        <>
            <div className="space-y-8">
                {/* General Settings */}
                <div className="space-y-4">
                    <div
                        className="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-2">
                        <div className="flex items-center gap-3">
                            <h3 className="text-sm font-bold text-slate-500 uppercase tracking-wider">
                                {t('settings.general')}
                            </h3>
                            {(saving || justSaved || Object.keys(pendingFields).length > 0) && (
                                <span
                                    className={`text-xs flex items-center gap-1.5 font-medium px-2 py-0.5 rounded-full transition-all ${(saving || Object.keys(pendingFields).length > 0)
                                        ? 'text-slate-500 bg-slate-100 dark:bg-slate-800 animate-pulse'
                                        : 'text-emerald-500 bg-emerald-50 dark:bg-emerald-900/20'
                                    }`}>
                                    {(saving || Object.keys(pendingFields).length > 0) ?
                                        <Loader2 className="w-3.5 h-3.5 animate-spin"/> :
                                        <Check className="w-3.5 h-3.5"/>}
                                    {(saving || Object.keys(pendingFields).length > 0) ? t('messages.saving') : t('messages.saved')}
                                </span>
                            )}
                        </div>
                    </div>

                    <div className="flex items-center justify-between">
                        <div>
                            <span
                                className="text-slate-900 dark:text-slate-200 font-medium block">{t('settings.muteAfterSpeech')}</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">
                                {t('settings.muteAfterSpeechDesc')}
                            </span>
                        </div>
                        <Toggle
                            checked={muteAfterSpeech}
                            onChange={toggleMute}
                            loading={pendingFields['mute']}
                            disabled={saving}
                        />
                    </div>

                    <div className="flex items-center justify-between">
                        <div>
                            <span
                                className="text-slate-900 dark:text-slate-200 font-medium block">{t('settings.doubleIdentities')}</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">
                                {t('settings.doubleIdentitiesDesc')}
                            </span>
                        </div>
                        <Toggle
                            checked={doubleIdentities}
                            onChange={toggleDoubleIdentities}
                            loading={pendingFields['double']}
                            disabled={saving}
                        />
                    </div>
                </div>

                {/* Player Count Settings */}
                <div className="space-y-4">
                    <h3 className="text-sm font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800 pb-2">
                        {t('settings.playerCount')}
                    </h3>
                    <div
                        className="flex items-center gap-6 bg-slate-50 dark:bg-slate-800/50 p-4 rounded-xl border border-slate-200 dark:border-slate-700">
                        <div className="flex-1">
                            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                                {t('settings.totalPlayers')}
                            </label>
                            <p className="text-xs text-slate-500 dark:text-slate-400">
                                {t('settings.playerCountDesc')}
                            </p>
                        </div>

                        {(playerCountChanged || updatingPlayerCount) && (
                            <button
                                onClick={handlePlayerCountUpdate}
                                disabled={updatingPlayerCount || !playerCountChanged}
                                className={`px-6 py-2 rounded-xl transition-all shadow-lg font-bold text-sm flex items-center gap-2 ${playerCountChanged
                                    ? 'bg-indigo-600 hover:bg-indigo-700 text-white shadow-indigo-200 dark:shadow-none'
                                    : 'bg-slate-100 text-slate-400 cursor-not-allowed dark:bg-slate-800'
                                }`}
                            >
                                {updatingPlayerCount ? <Loader2 className="w-4 h-4 animate-spin"/> :
                                    <Check className="w-4 h-4"/>}
                                {t('buttons.update')}
                            </button>
                        )}

                        <Counter
                            value={playerCount}
                            onIncrement={() => playerCount < 50 && setPlayerCount(prev => prev + 1)}
                            onDecrement={() => playerCount > 1 && setPlayerCount(prev => prev - 1)}
                            min={1}
                            max={50}
                            loading={updatingPlayerCount}
                            disabled={updatingPlayerCount}
                            variant="card"
                        />
                    </div>
                </div>

                {/* Roles Settings */}
                <div className="space-y-4">
                    <h3 className="text-sm font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800 pb-2 flex justify-between items-center">
                        <div className="flex items-center gap-4">
                            <span>{t('roles.title')}</span>
                            <span
                                className="text-xs font-normal normal-case opacity-70">{t('messages.totalCount')}: {roles.length}</span>
                        </div>
                        <button
                            onClick={handleRandomAssign}
                            disabled={assigningRoles}
                            className="text-xs flex items-center gap-1.5 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-400 px-3 py-1.5 rounded-full hover:bg-indigo-200 dark:hover:bg-indigo-900/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {assigningRoles ? <Loader2 className="w-4 h-4 animate-spin"/> :
                                <Dices className="w-4 h-4"/>}
                            {t('messages.randomAssignRoles')}
                        </button>
                    </h3>

                    {error && (
                        <div
                            className="bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 p-3 rounded-lg flex items-start gap-3 text-sm animate-in fade-in slide-in-from-top-2">
                            <AlertCircle className="w-5 h-5 shrink-0 mt-0.5"/>
                            <div className="flex-1">
                                <p className="font-medium">{t('errors.error')}</p>
                                <p className="opacity-90">{error}</p>
                            </div>
                            <button
                                onClick={() => setError(null)}
                                className="p-1 hover:bg-red-100 dark:hover:bg-red-900/40 rounded transition-colors"
                            >
                                <Minus className="w-4 h-4 rotate-45"/>
                            </button>
                        </div>
                    )}

                    {/* Add Role Control */}
                    <div className="flex gap-2">
                        <div className="relative flex-1">
                            <input
                                type="text"
                                value={selectedRole}
                                onChange={(e) => setSelectedRole(e.target.value)}
                                list="role-suggestions"
                                className="w-full bg-slate-100 dark:bg-slate-800 border-none rounded-lg px-4 py-2 text-slate-900 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500"
                                placeholder={t('messages.selectOrEnterRole')}
                                disabled={updatingRoles}
                            />
                            <datalist id="role-suggestions">
                                {AVAILABLE_ROLES.map(role => (
                                    <option key={role} value={role}/>
                                ))}
                            </datalist>
                        </div>
                        <button
                            onClick={() => handleAddRole(selectedRole)}
                            disabled={updatingRoles || !selectedRole.trim()}
                            className="flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg transition-colors"
                        >
                            {updatingRoles ? <Loader2 className="w-5 h-5 animate-spin"/> : <Plus className="w-5 h-5"/>}
                            <span className="hidden sm:inline">{t('messages.add')}</span>
                        </button>
                    </div>

                    {/* Roles List */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                        {Object.entries(roleCounts).sort((a, b) => b[1] - a[1]).map(([role, count]) => (
                            <div key={role}
                                 className="flex items-center justify-between p-3 bg-slate-50 dark:bg-slate-800/50 rounded-lg border border-slate-200 dark:border-slate-700">
                                <div className="flex items-center gap-2">
                                    <div
                                        className={`p-1.5 rounded-md ${role.includes('狼') || role === '惡靈騎士' || role === '石像鬼' ? 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400' :
                                            role === '平民' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400' :
                                                'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400'
                                        }`}>
                                        <Users className="w-4 h-4"/>
                                    </div>
                                    <span className="font-medium text-slate-700 dark:text-slate-200">{role}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <button
                                        onClick={() => handleRemoveRole(role)}
                                        disabled={updatingRoles}
                                        className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors"
                                    >
                                        <Minus className="w-4 h-4"/>
                                    </button>
                                    <span
                                        className="w-8 text-center font-bold text-slate-900 dark:text-white bg-white dark:bg-slate-900 rounded py-0.5 border border-slate-200 dark:border-slate-700">
                                        {count}
                                    </span>
                                    <button
                                        onClick={() => handleAddRole(role)}
                                        disabled={updatingRoles}
                                        className="p-1 text-slate-400 hover:text-green-500 hover:bg-green-50 dark:hover:bg-green-900/20 rounded transition-colors"
                                    >
                                        <Plus className="w-4 h-4"/>
                                    </button>
                                </div>
                            </div>
                        ))}

                        {roles.length === 0 && (
                            <div
                                className="col-span-full py-8 text-center text-slate-400 border-2 border-dashed border-slate-200 dark:border-slate-800 rounded-lg">
                                <AlertCircle className="w-8 h-8 mx-auto mb-2 opacity-50"/>
                                {t('messages.noRolesConfigured')}
                            </div>
                        )}
                    </div>
                </div>
            </div>


            {/* Special Settings Section */}
            <div className="space-y-4">
                <h3 className="text-sm font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800 pb-2">
                    {t('gameSettings.gameSettings') || 'Special Settings'}
                </h3>

                {/* Witch Can Save Self */}
                <div className="flex items-center justify-between">
                    <div>
                        <span
                            className="text-slate-900 dark:text-slate-200 font-medium block">{t('gameSettings.witchCanSaveSelf')}</span>
                        <span className="text-xs text-slate-500 dark:text-slate-400">
                            {t('gameSettings.witchCanSaveSelfDesc')}
                        </span>
                    </div>
                    <Toggle
                        checked={witchCanSaveSelf}
                        onChange={toggleWitchCanSaveSelf}
                        loading={pendingFields['witchSave']}
                        disabled={saving}
                    />
                </div>

                {/* Allow Wolf Self Kill */}
                <div className="flex items-center justify-between">
                    <div>
                        <span
                            className="text-slate-900 dark:text-slate-200 font-medium block">{t('gameSettings.allowWolfSelfKill')}</span>
                        <span className="text-xs text-slate-500 dark:text-slate-400">
                            {t('gameSettings.allowWolfSelfKillDesc')}
                        </span>
                    </div>
                    <Toggle
                        checked={allowWolfSelfKill}
                        onChange={toggleAllowWolfSelfKill}
                        loading={pendingFields['wolfSelfKill']}
                        disabled={saving}
                    />
                </div>

                {/* Hidden Role On Death */}
                <div className="flex items-center justify-between">
                    <div>
                        <span
                            className="text-slate-900 dark:text-slate-200 font-medium block">{t('gameSettings.hiddenRoleOnDeath')}</span>
                        <span className="text-xs text-slate-500 dark:text-slate-400">
                            {t('gameSettings.hiddenRoleOnDeathDesc')}
                        </span>
                    </div>
                    <Toggle
                        checked={hiddenRoleOnDeath}
                        onChange={toggleHiddenRoleOnDeath}
                        loading={pendingFields['hiddenRole']}
                        disabled={saving}
                    />
                </div>
            </div>

        </>
    );
};
