import React, {useEffect, useRef, useState} from 'react';
import {
    AlertCircle,
    Check,
    Eye,
    LayoutDashboard,
    Loader2,
    Minus,
    Moon,
    Plus,
    Settings2,
    Shield,
    Swords,
    Users,
    Zap
} from 'lucide-react';
import {useParams} from 'react-router-dom';
import {useTranslation} from '@/lib/i18n';
import {useMutation} from '@tanstack/react-query';
import {
    addRoleMutation,
    removeRoleMutation,
    setPlayerCountMutation,
    updateSettingsMutation
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
    const [pendingFields, setPendingFields] = useState<Record<string, boolean>>({});
    const [error, setError] = useState<string | null>(null);

    const {gameState} = useGameState(guildId, user);
    const loading = !gameState;

    // Mutations
    const updateSettingsMut = useMutation(updateSettingsMutation());
    const addRoleMut = useMutation(addRoleMutation());
    const removeRoleMut = useMutation(removeRoleMutation());
    const setPlayerCountMut = useMutation(setPlayerCountMutation());

    const AVAILABLE_ROLES = [
        "平民", "狼人", "女巫", "預言家", "獵人",
        "守衛", "白痴", "騎士", "守墓人", "攝夢人", "魔術師",
        "狼王", "白狼王", "狼兄", "狼弟", "隱狼", "石像鬼",
        "惡靈騎士", "血月使者", "機械狼", "複製人"
    ];

    const ROLE_FACTIONS: Record<string, string[]> = {
        werewolf: ["WEREWOLF", "狼人", "狼王", "白狼王", "狼兄", "狼弟", "隱狼", "石像鬼", "惡靈騎士", "血月使者", "機械狼"],
        special: ["WITCH", "SEER", "HUNTER", "GUARD", "女巫", "預言家", "獵人", "守衛", "白痴", "騎士", "守墓人", "攝夢人", "魔術師", "複製人"],
        civilian: ["VILLAGER", "平民", "村民"]
    };

    const getRoleIcon = (role: string) => {
        const uRole = role.toUpperCase();
        if (ROLE_FACTIONS.werewolf.includes(role) || ROLE_FACTIONS.werewolf.includes(uRole)) return <Moon
            className="w-5 h-5 text-red-500"/>;
        if (role === 'SEER' || role === '預言家') return <Eye className="w-5 h-5 text-purple-500"/>;
        if (role === 'WITCH' || role === '女巫') return <Zap className="w-5 h-5 text-pink-500"/>;
        if (role === 'HUNTER' || role === '獵人') return <Swords className="w-5 h-5 text-orange-500"/>;
        if (role === 'GUARD' || role === '守衛') return <Shield className="w-5 h-5 text-blue-500"/>;
        if (role === 'VILLAGER' || role === '平民' || role === '村民') return <Users
            className="w-5 h-5 text-emerald-500"/>;
        return <Users className="w-5 h-5 text-slate-400"/>;
    };

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

        const session = gameState;
        const rolesArray = session?.roles;

        if (!session || !rolesArray) return;

        setMuteAfterSpeech(session.muteAfterSpeech);
        setDoubleIdentities(session.doubleIdentities);

        if (session.settings) {
            setWitchCanSaveSelf(session.settings.witchCanSaveSelf ?? true);
            setAllowWolfSelfKill(session.settings.allowWolfSelfKill ?? true);
            setHiddenRoleOnDeath(session.settings.hiddenRoleOnDeath ?? false);
        }

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

    // Role Faction Mapping
    const assignedRoles = Object.keys(roleCounts);
    const factionConfigs = [
        {id: 'werewolf', label: t('roles.factions.werewolf'), roles: ROLE_FACTIONS.werewolf},
        {id: 'special', label: t('roles.factions.special'), roles: ROLE_FACTIONS.special},
        {id: 'civilian', label: t('roles.factions.civilian'), roles: ROLE_FACTIONS.civilian}
    ];

    const categorizedRoleSet = new Set(Object.values(ROLE_FACTIONS).flat());
    const othersList = assignedRoles.filter(role => !categorizedRoleSet.has(role) && !categorizedRoleSet.has(role.toUpperCase()));

    if (othersList.length > 0) {
        factionConfigs.push({
            id: 'others',
            label: t('roles.factions.others'),
            roles: othersList
        });
    }

    const progress = Math.min((roles.length / playerCount) * 100, 100);
    const isReady = roles.length === playerCount;

    return (
        <div className="max-w-7xl mx-auto space-y-10 pb-20 px-8 font-['Spline_Sans']">
            {/* Header section - Stitch Design */}
            <header className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="space-y-1.5 shrink-0">
                    <div
                        className="flex items-center gap-2 text-primary dark:text-indigo-400 font-bold text-sm tracking-wide uppercase">
                        <LayoutDashboard className="w-4 h-4"/>
                        <span>{t('sidebar.dashboard')}</span>
                    </div>
                    <div className="flex items-center gap-4">
                        <h1 className="text-4xl font-black text-slate-900 dark:text-white tracking-tight">
                            {t('settings.title')}
                        </h1>

                        {(saving || justSaved || Object.keys(pendingFields).length > 0) && (
                            <div
                                className={`flex items-center gap-2 px-3 py-1.5 text-sm font-bold transition-all duration-500 ${(saving || Object.keys(pendingFields).length > 0)
                                    ? 'text-slate-400'
                                    : 'text-emerald-500'
                                }`}>
                                {(saving || Object.keys(pendingFields).length > 0) ? (
                                    <Loader2 className="w-4 h-4 animate-spin"/>
                                ) : (
                                    <Check className="w-4 h-4"/>
                                )}
                                <span className="uppercase tracking-widest text-[10px]">
                                    {(saving || Object.keys(pendingFields).length > 0) ? t('messages.saving') : t('messages.saved')}
                                </span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Role Assignment Banner - Right Part (Stitch Style) */}
                <div className="flex items-center gap-6">
                    <div className="flex flex-col items-end gap-1">
                        <div className="flex items-center gap-4">
                            <div className="text-right">
                                <span
                                    className="text-[10px] font-black uppercase tracking-[0.2em] text-slate-400 dark:text-slate-500 block">
                                    {t('roles.status')}
                                </span>
                                <div className="flex items-baseline gap-1.5">
                                    <span
                                        className="text-2xl font-black text-slate-900 dark:text-white">{roles.length}</span>
                                    <span
                                        className="text-sm font-bold text-slate-400 tracking-tight">/ {playerCount}</span>
                                </div>
                            </div>

                            <div
                                className={`px-4 py-2 rounded-xl flex items-center gap-2 border-2 transition-all duration-500 ${isReady
                                    ? 'bg-emerald-50 border-emerald-100 text-emerald-600 dark:bg-emerald-900/20 dark:border-emerald-800/50 dark:text-emerald-400 shadow-lg shadow-emerald-100 dark:shadow-none'
                                    : 'bg-red-50 border-red-100 text-red-600 dark:bg-red-900/20 dark:border-red-800/50 dark:text-red-400 shadow-lg shadow-red-100 dark:shadow-none animate-pulse'
                                }`}>
                                {isReady ? <Check className="w-4 h-4"/> : <AlertCircle className="w-4 h-4"/>}
                                <span className="text-xs font-black uppercase tracking-wider">
                                    {isReady ? t('messages.saved') : t('roles.warning')}
                                </span>
                            </div>
                        </div>
                        {/* Progress line */}
                        <div className="w-full h-1.5 bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                            <div
                                className={`h-full transition-all duration-700 ease-out rounded-full ${isReady ? 'bg-emerald-500' : 'bg-red-500'}`}
                                style={{width: `${progress}%`}}
                            />
                        </div>
                    </div>
                </div>
            </header>

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-12">
                {/* Left Column: Rules (Wider Panel) */}
                <div className="lg:col-span-7 space-y-10">
                    {/* Player Count */}
                    <section className="space-y-4">
                        <div
                            className="flex items-center gap-2 text-slate-900 dark:text-white font-black uppercase tracking-[0.15em]">
                            <Users className="w-5 h-5 text-blue-600"/>
                            <h2 className="text-xl">{t('settings.playerCount')}</h2>
                        </div>
                        <div
                            className="bg-white dark:bg-slate-900/40 rounded-3xl border border-slate-200 dark:border-slate-800 p-6 shadow-sm">
                            <div className="flex items-center justify-between gap-8">
                                <div className="space-y-1.5 flex-1">
                                    <label className="text-lg font-bold text-slate-900 dark:text-slate-100 block">
                                        {t('settings.totalPlayers')}
                                    </label>
                                    <p className="text-sm text-slate-500 dark:text-slate-400 leading-relaxed font-medium">
                                        {t('settings.playerCountDesc')}
                                    </p>
                                </div>
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

                            {(playerCountChanged || updatingPlayerCount) && (
                                <button
                                    onClick={handlePlayerCountUpdate}
                                    disabled={updatingPlayerCount || !playerCountChanged}
                                    className="mt-6 w-full flex items-center justify-center gap-3 bg-primary hover:bg-primary/90 disabled:bg-slate-200 dark:disabled:bg-slate-800 text-white font-black py-3.5 rounded-2xl transition-all shadow-xl shadow-primary/20 dark:shadow-none text-sm uppercase tracking-widest"
                                >
                                    {updatingPlayerCount ? <Loader2 className="w-5 h-5 animate-spin"/> :
                                        <Check className="w-5 h-5"/>}
                                    {t('buttons.update')}
                                </button>
                            )}
                        </div>
                    </section>

                    {/* General Rules */}
                    <section className="space-y-4">
                        <div
                            className="flex items-center gap-2 text-slate-900 dark:text-white font-black uppercase tracking-[0.15em]">
                            <Settings2 className="w-5 h-5 text-blue-600"/>
                            <h2 className="text-xl">{t('settings.general')}</h2>
                        </div>
                        <div
                            className="bg-white dark:bg-slate-900/40 rounded-3xl border border-slate-200 dark:border-slate-800 overflow-hidden shadow-sm divide-y divide-slate-100 dark:divide-slate-800/50">
                            {[
                                {
                                    label: t('settings.muteAfterSpeech'),
                                    desc: t('settings.muteAfterSpeechDesc'),
                                    checked: muteAfterSpeech,
                                    onChange: toggleMute,
                                    id: 'mute'
                                },
                                {
                                    label: t('settings.doubleIdentities'),
                                    desc: t('settings.doubleIdentitiesDesc'),
                                    checked: doubleIdentities,
                                    onChange: toggleDoubleIdentities,
                                    id: 'double'
                                },
                                {
                                    label: t('gameSettings.witchCanSaveSelf'),
                                    desc: t('gameSettings.witchCanSaveSelfDesc'),
                                    checked: witchCanSaveSelf,
                                    onChange: toggleWitchCanSaveSelf,
                                    id: 'witchSave'
                                },
                                {
                                    label: t('gameSettings.allowWolfSelfKill'),
                                    desc: t('gameSettings.allowWolfSelfKillDesc'),
                                    checked: allowWolfSelfKill,
                                    onChange: toggleAllowWolfSelfKill,
                                    id: 'wolfSelfKill'
                                },
                                {
                                    label: t('gameSettings.hiddenRoleOnDeath'),
                                    desc: t('gameSettings.hiddenRoleOnDeathDesc'),
                                    checked: hiddenRoleOnDeath,
                                    onChange: toggleHiddenRoleOnDeath,
                                    id: 'hiddenRole'
                                }
                            ].map((rule) => (
                                <div key={rule.id}
                                     className="p-5 flex items-center justify-between hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors">
                                    <div className="space-y-1">
                                        <span className="text-base font-bold text-slate-900 dark:text-slate-100 block">
                                            {rule.label}
                                        </span>
                                        <p className="text-sm text-slate-500 dark:text-slate-400 font-medium">
                                            {rule.desc}
                                        </p>
                                    </div>
                                    <Toggle
                                        checked={rule.checked}
                                        onChange={rule.onChange}
                                        loading={pendingFields[rule.id]}
                                        disabled={saving}
                                    />
                                </div>
                            ))}
                        </div>
                    </section>
                </div>

                {/* Right Column: Roles */}
                <div className="lg:col-span-5 space-y-6 text-slate-600 dark:text-slate-400">
                    <div className="flex items-center justify-between h-8">
                        <div
                            className="flex items-center gap-2 text-slate-900 dark:text-white font-black uppercase tracking-[0.15em]">
                            <Users className="w-5 h-5 text-blue-600"/>
                            <h2 className="text-xl">{t('roles.title')}</h2>
                        </div>
                    </div>

                    {error && (
                        <div
                            className="bg-red-50 dark:bg-red-900/10 border border-red-100 dark:border-red-900/20 p-4 rounded-2xl flex items-start gap-4">
                            <AlertCircle className="w-5 h-5 text-red-500 shrink-0 mt-0.5"/>
                            <div className="flex-1">
                                <p className="font-bold text-red-600 dark:text-red-400">{t('errors.error')}</p>
                                <p className="text-sm text-red-500/90 dark:text-red-400/80">{error}</p>
                            </div>
                            <button onClick={() => setError(null)}
                                    className="p-1 text-red-400 hover:text-red-500 transition-colors">
                                <Minus className="w-4 h-4 rotate-45"/>
                            </button>
                        </div>
                    )}

                    <div className="space-y-8">
                        {factionConfigs.map(faction => {
                            const factionRoles = Object.entries(roleCounts).filter(([role]) => {
                                if (faction.id === 'others') return othersList.includes(role);
                                return faction.roles.includes(role) || faction.roles.includes(role.toUpperCase());
                            });

                            if (factionRoles.length === 0 && faction.id !== 'special' && faction.id !== 'others') return null;
                            if (factionRoles.length === 0 && faction.id === 'others') return null;

                            return (
                                <div key={faction.id} className="space-y-3">
                                    <div className="flex items-center gap-2">
                                        <div
                                            className={`h-1 w-1 rounded-full ${faction.id === 'werewolf' ? 'bg-red-500' : faction.id === 'special' ? 'bg-amber-500' : faction.id === 'civilian' ? 'bg-emerald-500' : 'bg-slate-500'}`}/>
                                        <span
                                            className="text-[10px] font-black uppercase tracking-[0.2em] text-slate-400 dark:text-slate-500">
                                            {faction.label}
                                        </span>
                                    </div>
                                    <div className="space-y-2">
                                        {factionRoles.sort((a, b) => b[1] - a[1]).map(([role, count]) => (
                                            <div key={role}
                                                 className="group flex items-center justify-between p-3 px-4 bg-white dark:bg-slate-900/30 rounded-2xl border border-slate-200 dark:border-slate-800 shadow-sm hover:border-primary/30 Transition-all duration-300">
                                                <div className="flex items-center gap-4">
                                                    <div
                                                        className={`p-2.5 rounded-xl transition-transform group-hover:scale-105 duration-500 ${faction.id === 'werewolf' ? 'bg-red-50 dark:bg-red-900/10' :
                                                            faction.id === 'special' ? 'bg-amber-50 dark:bg-amber-900/10' :
                                                                faction.id === 'civilian' ? 'bg-emerald-50 dark:bg-emerald-900/10' :
                                                                    'bg-slate-50 dark:bg-slate-800/50'
                                                        }`}>
                                                        {getRoleIcon(role)}
                                                    </div>
                                                    <span
                                                        className="font-bold text-lg text-slate-900 dark:text-slate-100 tracking-tight">
                                                        {role}
                                                    </span>
                                                </div>
                                                <div
                                                    className="flex items-center gap-2 bg-slate-50 dark:bg-slate-950/50 p-1 rounded-xl border border-slate-100 dark:border-slate-800">
                                                    <button
                                                        onClick={() => handleRemoveRole(role)}
                                                        className="p-1 px-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-all"
                                                    >
                                                        <Minus className="w-4 h-4"/>
                                                    </button>
                                                    <span
                                                        className="min-w-[2rem] text-center font-black text-xl text-slate-900 dark:text-white">
                                                        {count}
                                                    </span>
                                                    <button
                                                        onClick={() => handleAddRole(role)}
                                                        className="p-1 px-2 text-slate-400 hover:text-primary hover:bg-primary/5 dark:hover:bg-primary/10 rounded-lg transition-all"
                                                    >
                                                        <Plus className="w-4 h-4"/>
                                                    </button>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <div className="pt-6">
                        <div className="relative group">
                            <input
                                type="text"
                                value={selectedRole}
                                onChange={(e) => setSelectedRole(e.target.value)}
                                list="role-suggestions"
                                className="w-full bg-slate-50 dark:bg-slate-900/30 border-2 border-slate-200 dark:border-slate-800 rounded-3xl px-6 py-4 text-slate-900 dark:text-slate-100 focus:ring-8 focus:ring-primary/5 focus:border-primary outline-none transition-all pr-16 font-bold placeholder:text-slate-400 dark:placeholder:text-slate-600"
                                placeholder={t('messages.selectOrEnterRole')}
                                disabled={updatingRoles}
                            />
                            <datalist id="role-suggestions">
                                {AVAILABLE_ROLES.map(role => (
                                    <option key={role} value={role}/>
                                ))}
                            </datalist>
                            <button
                                onClick={() => handleAddRole(selectedRole)}
                                disabled={updatingRoles || !selectedRole.trim()}
                                className="absolute right-2 top-2 p-3 bg-primary hover:bg-primary/90 text-white rounded-2xl transition-all shadow-lg shadow-primary/30 disabled:bg-slate-300 dark:disabled:bg-slate-800"
                            >
                                {updatingRoles ? <Loader2 className="w-5 h-5 animate-spin"/> :
                                    <Plus className="w-5 h-5"/>}
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};
