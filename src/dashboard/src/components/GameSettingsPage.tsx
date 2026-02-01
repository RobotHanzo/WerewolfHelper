import React, { useState, useEffect, useRef } from 'react';
import { RefreshCw, Loader2, Check, Plus, Minus, Users, AlertCircle, Dices } from 'lucide-react';
import { ProgressOverlay } from './ProgressOverlay';
import { useParams } from 'react-router-dom';
import { useTranslation } from '../lib/i18n';
import { api } from '../lib/api';
import { useWebSocket } from '../lib/websocket';

export const GameSettingsPage: React.FC = () => {
    const { guildId } = useParams<{ guildId: string }>();
    const { t } = useTranslation();

    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [justSaved, setJustSaved] = useState(false);
    const [muteAfterSpeech, setMuteAfterSpeech] = useState(true);
    const [doubleIdentities, setDoubleIdentities] = useState(false);
    const [roles, setRoles] = useState<string[]>([]);
    const [roleCounts, setRoleCounts] = useState<Record<string, number>>({});

    // New State Variables
    const [playerCount, setPlayerCount] = useState<number>(12);
    const [selectedRole, setSelectedRole] = useState<string>('');
    const [updatingRoles, setUpdatingRoles] = useState(false);

    // Overlay State
    const [overlayVisible, setOverlayVisible] = useState(false);
    const [overlayStatus, setOverlayStatus] = useState<'processing' | 'success' | 'error'>('processing');
    const [overlayTitle, setOverlayTitle] = useState('');
    const [overlayLogs, setOverlayLogs] = useState<string[]>([]);
    const [overlayError, setOverlayError] = useState<string | undefined>(undefined);
    const [overlayProgress, setOverlayProgress] = useState(0);

    const AVAILABLE_ROLES = [
        "平民", "狼人", "女巫", "預言家", "獵人",
        "守衛", "白痴", "騎士", "守墓人", "攝夢人", "魔術師",
        "狼王", "白狼王", "狼兄", "狼弟", "隱狼", "石像鬼",
        "惡靈騎士", "血月使者", "機械狼", "複製人"
    ];

    const isFirstLoad = useRef(true);

    // Auto-save effect
    useEffect(() => {
        if (isFirstLoad.current) {
            isFirstLoad.current = false;
            return;
        }

        if (loading) return;

        const saveSettings = async () => {
            if (!guildId) return;
            setSaving(true);
            try {
                await api.updateSettings(guildId, {
                    muteAfterSpeech,
                    doubleIdentities
                });
            } catch (e) {
                console.error("Failed to update settings", e);
            } finally {
                setJustSaved(true);
                setTimeout(() => setSaving(false), 500);
                setTimeout(() => setJustSaved(false), 2000);
            }
        };

        const timeoutId = setTimeout(saveSettings, 500);
        return () => clearTimeout(timeoutId);
    }, [muteAfterSpeech, doubleIdentities, guildId]);

    const loadSettings = async () => {
        if (!guildId) return;
        setLoading(true);
        isFirstLoad.current = true;
        try {
            const [sessionData, rolesData]: [any, any] = await Promise.all([
                api.getSession(guildId),
                api.getRoles(guildId)
            ]);

            setMuteAfterSpeech(sessionData.muteAfterSpeech);
            setDoubleIdentities(sessionData.doubleIdentities);

            // Set player count from current players length
            if (Array.isArray(sessionData.players)) {
                setPlayerCount(sessionData.players.length);
            }

            setRoles(rolesData.filter((r: unknown): r is string => typeof r === 'string') || []);
        } catch (e) {
            console.error("Failed to load settings", e);
        } finally {
            setLoading(false);
            setTimeout(() => { isFirstLoad.current = false; }, 100);
        }
    };

    useEffect(() => {
        if (guildId) {
            loadSettings();
        }
    }, [guildId]);

    useEffect(() => {
        const counts: Record<string, number> = {};
        roles.forEach(role => {
            counts[role] = (counts[role] || 0) + 1;
        });
        setRoleCounts(counts);
    }, [roles]);

    const handleAddRole = async (role: string) => {
        if (!guildId || updatingRoles) return;
        setUpdatingRoles(true);
        try {
            await api.addRole(guildId, role, 1);
            const newRoles = await api.getRoles(guildId) as string[];
            setRoles(newRoles.filter((r: unknown): r is string => typeof r === 'string') || []);
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
            await api.removeRole(guildId, role, 1);
            const newRoles = await api.getRoles(guildId) as string[];
            setRoles(newRoles.filter((r: unknown): r is string => typeof r === 'string') || []);
        } catch (e) {
            console.error("Failed to remove role", e);
        } finally {
            setUpdatingRoles(false);
        }
    };

    const handleRandomAssign = async () => {
        if (!guildId) return;

        setOverlayTitle(t('messages.randomAssignRoles'));
        setOverlayVisible(true);
        setOverlayStatus('processing');
        setOverlayLogs([t('overlayMessages.requestingAssign')]);
        setOverlayError(undefined);
        setOverlayProgress(0);

        try {
            await api.assignRoles(guildId);
            setOverlayLogs(prev => [...prev]);
            setOverlayStatus('success');
            setOverlayLogs(prev => [...prev, t('overlayMessages.assignSuccess')]);

        } catch (error: any) {
            console.error("Assign failed", error);
            setOverlayStatus('error');
            const errorMessage = error.message || t('errors.unknownError');
            setOverlayLogs(prev => [...prev, `${t('errors.error')}: ${errorMessage}`]);
            setOverlayError(errorMessage);
        }
    };

    const handlePlayerCountUpdate = async () => {
        if (!guildId) return;

        setOverlayTitle(t('settings.playerCount'));
        setOverlayVisible(true);
        setOverlayStatus('processing');
        setOverlayLogs([t('overlayMessages.updatingPlayerCount')]);
        setOverlayError(undefined);
        setOverlayProgress(0);

        try {
            await api.setPlayerCount(guildId, playerCount);
            setOverlayProgress(100);
            setOverlayStatus('success');
            setOverlayLogs(prev => [...prev, t('overlayMessages.playerCountUpdateSuccess')]);

            // Reload settings to refresh exact state
            loadSettings();
        } catch (error: any) {
            console.error("Update failed", error);
            setOverlayStatus('error');
            const errorMessage = error.message || t('errors.actionFailed', { action: t('buttons.update') });
            setOverlayLogs(prev => [...prev, `${t('errors.error')}: ${errorMessage}`]);
            setOverlayError(errorMessage);
        }
    };

    if (loading) {
        return (
            <div className="flex justify-center items-center h-64">
                <Loader2 className="w-8 h-8 animate-spin text-indigo-600" />
            </div>
        );
    }

    return (
        <>
            <div className="space-y-8">
                {/* General Settings */}
                <div className="space-y-4">
                    <h3 className="text-sm font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800 pb-2">
                        {t('settings.general')}
                    </h3>

                    <div className="flex items-center justify-between">
                        <div>
                            <span className="text-slate-900 dark:text-slate-200 font-medium block">{t('settings.muteAfterSpeech')}</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">
                                {t('settings.muteAfterSpeechDesc')}
                            </span>
                        </div>
                        <div className="flex items-center gap-3">
                            {(saving || justSaved) && (
                                <div className="flex items-center justify-center w-5 h-5">
                                    {saving ? (
                                        <Loader2 className="w-4 h-4 animate-spin text-slate-400" />
                                    ) : (
                                        <Check className="w-4 h-4 text-emerald-500" />
                                    )}
                                </div>
                            )}
                            <label className={`relative inline-flex items-center ${saving ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'}`}>
                                <input
                                    type="checkbox"
                                    checked={muteAfterSpeech}
                                    onChange={(e) => !saving && setMuteAfterSpeech(e.target.checked)}
                                    disabled={saving}
                                    className="sr-only peer"
                                />
                                <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer dark:bg-slate-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-indigo-600"></div>
                            </label>
                        </div>
                    </div>

                    <div className="flex items-center justify-between">
                        <div>
                            <span className="text-slate-900 dark:text-slate-200 font-medium block">{t('settings.doubleIdentities')}</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400">
                                {t('settings.doubleIdentitiesDesc')}
                            </span>
                        </div>
                        <div className="flex items-center gap-3">
                            {(saving || justSaved) && (
                                <div className="flex items-center justify-center w-5 h-5">
                                    {saving ? (
                                        <Loader2 className="w-4 h-4 animate-spin text-slate-400" />
                                    ) : (
                                        <Check className="w-4 h-4 text-emerald-500" />
                                    )}
                                </div>
                            )}
                            <label className={`relative inline-flex items-center ${saving ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'}`}>
                                <input
                                    type="checkbox"
                                    checked={doubleIdentities}
                                    onChange={(e) => !saving && setDoubleIdentities(e.target.checked)}
                                    disabled={saving}
                                    className="sr-only peer"
                                />
                                <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full peer dark:bg-slate-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-indigo-600"></div>
                            </label>
                        </div>
                    </div>
                </div>

                {/* Player Count Settings */}
                <div className="space-y-4">
                    <h3 className="text-sm font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800 pb-2">
                        {t('settings.playerCount')}
                    </h3>
                    <div className="flex items-end gap-4">
                        <div className="flex-1">
                            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">
                                {t('settings.totalPlayers')}
                            </label>
                            <input
                                type="number"
                                min="1"
                                max="50"
                                value={playerCount}
                                onChange={(e) => setPlayerCount(parseInt(e.target.value) || 0)}
                                className="w-full bg-slate-100 dark:bg-slate-800 border-none rounded-lg px-4 py-2 text-slate-900 dark:text-slate-200 focus:ring-2 focus:ring-indigo-500"
                            />
                            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                                {t('settings.playerCountDesc')}
                            </p>
                        </div>
                        <button
                            onClick={handlePlayerCountUpdate}
                            className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg transition-colors h-10 mb-0.5"
                        >
                            {t('buttons.update')}
                        </button>
                    </div>
                </div>

                {/* Roles Settings */}
                <div className="space-y-4">
                    <h3 className="text-sm font-bold text-slate-500 uppercase tracking-wider border-b border-slate-200 dark:border-slate-800 pb-2 flex justify-between items-center">
                        <div className="flex items-center gap-4">
                            <span>{t('roles.title')}</span>
                            <span className="text-xs font-normal normal-case opacity-70">{t('messages.totalCount')}: {roles.length}</span>
                        </div>
                        <button
                            onClick={handleRandomAssign}
                            className="text-xs flex items-center gap-1.5 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-400 px-3 py-1.5 rounded-full hover:bg-indigo-200 dark:hover:bg-indigo-900/50 transition-colors"
                        >
                            <Dices className="w-4 h-4" />
                            {t('messages.randomAssignRoles')}
                        </button>
                    </h3>

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
                                    <option key={role} value={role} />
                                ))}
                            </datalist>
                        </div>
                        <button
                            onClick={() => handleAddRole(selectedRole)}
                            disabled={updatingRoles}
                            className="flex items-center gap-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg transition-colors"
                        >
                            {updatingRoles ? <Loader2 className="w-5 h-5 animate-spin" /> : <Plus className="w-5 h-5" />}
                            <span className="hidden sm:inline">{t('messages.add')}</span>
                        </button>
                    </div>

                    {/* Roles List */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                        {Object.entries(roleCounts).sort((a, b) => b[1] - a[1]).map(([role, count]) => (
                            <div key={role} className="flex items-center justify-between p-3 bg-slate-50 dark:bg-slate-800/50 rounded-lg border border-slate-200 dark:border-slate-700">
                                <div className="flex items-center gap-2">
                                    <div className={`p-1.5 rounded-md ${role.includes('狼') || role === '惡靈騎士' || role === '石像鬼' ? 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400' :
                                        role === '平民' ? 'bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400' :
                                            'bg-amber-100 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400'
                                        }`}>
                                        <Users className="w-4 h-4" />
                                    </div>
                                    <span className="font-medium text-slate-700 dark:text-slate-200">{role}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <button
                                        onClick={() => handleRemoveRole(role)}
                                        disabled={updatingRoles}
                                        className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors"
                                    >
                                        <Minus className="w-4 h-4" />
                                    </button>
                                    <span className="w-8 text-center font-bold text-slate-900 dark:text-white bg-white dark:bg-slate-900 rounded py-0.5 border border-slate-200 dark:border-slate-700">
                                        {count}
                                    </span>
                                    <button
                                        onClick={() => handleAddRole(role)}
                                        disabled={updatingRoles}
                                        className="p-1 text-slate-400 hover:text-green-500 hover:bg-green-50 dark:hover:bg-green-900/20 rounded transition-colors"
                                    >
                                        <Plus className="w-4 h-4" />
                                    </button>
                                </div>
                            </div>
                        ))}

                        {roles.length === 0 && (
                            <div className="col-span-full py-8 text-center text-slate-400 border-2 border-dashed border-slate-200 dark:border-slate-800 rounded-lg">
                                <AlertCircle className="w-8 h-8 mx-auto mb-2 opacity-50" />
                                {t('messages.noRolesConfigured')}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            <ProgressOverlay
                isVisible={overlayVisible}
                title={overlayTitle || t('overlayMessages.processing')}
                status={overlayStatus}
                logs={overlayLogs}
                error={overlayError}
                progress={overlayProgress}
                onComplete={() => setOverlayVisible(false)}
            />
        </>
    );
};
