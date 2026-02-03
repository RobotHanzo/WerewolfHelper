import {useEffect, useState} from 'react';
import {api} from '@/lib/api';
import {useGameState} from '@/features/game/hooks/useGameState';
import {useTranslation} from '@/lib/i18n';
import {useToast} from './Toast';
import {CheckCircle2} from 'lucide-react';

interface AvailableAction {
    actionId: string;
    roleName: string;
    priority: number;
    timing: string;
    targetCount: number;
    usageLimit: number;
    requiresAliveTarget: boolean;
}

interface PlayerWithActions {
    userId: string;
    nickname: string;
    actions: AvailableAction[];
}

export function JudgeActionPanel({guildId}: { guildId: string }) {
    const [playersWithActions, setPlayersWithActions] = useState<PlayerWithActions[]>([]);
    const [selectedAction, setSelectedAction] = useState<{
        playerId: string;
        actionId: string;
    } | null>(null);
    const [selectedTargets, setSelectedTargets] = useState<string[]>([]);
    const [loading, setLoading] = useState(false);
    const [submitted, setSubmitted] = useState(false);
    const {gameState} = useGameState(guildId, null);
    const {t} = useTranslation();
    const toast = useToast();

    useEffect(() => {
        loadAvailableActions();
    }, [guildId, gameState?.players]);

    const loadAvailableActions = async () => {
        try {
            const response = await api.getAvailableActions(guildId) as any;
            console.log('[JudgeActionPanel] Response:', response);

            if (response?.actions && typeof response.actions === 'object') {
                const players: PlayerWithActions[] = [];

                for (const [userId, actions] of Object.entries(response.actions as Record<string, AvailableAction[]>)) {
                    console.log('[JudgeActionPanel] Searching for userId:', userId, 'type:', typeof userId);
                    const player = gameState?.players.find(p => {
                        const match = String(p.userId) === String(userId);
                        console.log('[JudgeActionPanel] Comparing player', p.name, p.userId, '===', userId, ':', match);
                        return match;
                    });
                    console.log('[JudgeActionPanel] Found player:', player?.name, 'Actions:', (actions as AvailableAction[]).length);
                    if (player && (actions as AvailableAction[]).length > 0) {
                        players.push({
                            userId,
                            nickname: player.name,
                            actions: actions as AvailableAction[]
                        });
                    }
                }

                console.log('[JudgeActionPanel] Final players with actions:', players);
                setPlayersWithActions(players);
            }
        } catch (error) {
            console.error('Failed to load available actions:', error);
        }
    };

    const getActionLabel = (actionId: string) => t(`actions.labels.${actionId}`, actionId);

    const translateActionError = (error: string) => {
        if (!error) return error;
        if (error.startsWith('Actor does not have role ')) {
            const roleName = error.replace('Actor does not have role ', '');
            return t('actions.errors.actorMissingRole', 'Actor does not have role {role}', {role: roleName});
        }
        return t(`actions.errors.${error}`, error);
    };

    const handleSubmitAction = async () => {
        if (!selectedAction || selectedTargets.length === 0) {
            toast.error(t('actions.ui.selectActionAndTargets', 'Please select action and targets'));
            return;
        }

        setLoading(true);
        setSubmitted(false);
        try {
            const result = await api.submitRoleAction(
                guildId,
                selectedAction.actionId,
                selectedAction.playerId.toString(),
                selectedTargets.map(t => t.toString()),
                'JUDGE'
            ) as any;

            if (result?.success) {
                setSubmitted(true);
                setTimeout(() => {
                    setSelectedAction(null);
                    setSelectedTargets([]);
                    setSubmitted(false);
                    loadAvailableActions();
                }, 2000);
            } else {
                const translatedError = translateActionError(result?.error);
                toast.error(t('actions.ui.submitFailed', 'Failed to submit action: {error}', {error: translatedError}));
            }
        } catch (error) {
            toast.error(t('actions.ui.error', 'Error: {error}', {error: String(error)}));
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="space-y-4">
            {playersWithActions.length === 0 ? (
                <div
                    className="p-4 rounded-lg border border-dashed border-slate-300 dark:border-slate-700 bg-slate-50 dark:bg-slate-800/50">
                    <p className="text-center text-slate-500 dark:text-slate-400">{t('speechManager.noPlayersWithAvailableActions') || 'No players with available actions'}</p>
                </div>
            ) : (
                <>
                    {playersWithActions.map(player => (
                        <div key={player.userId}
                             className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden shadow-sm hover:shadow-md transition-shadow">
                            <div className="p-4">
                                <div className="flex items-center gap-3 mb-3">
                                    <div
                                        className="w-8 h-8 rounded-full bg-indigo-100 dark:bg-indigo-900/30 flex items-center justify-center">
                                        <span
                                            className="text-sm font-bold text-indigo-600 dark:text-indigo-400">⚔</span>
                                    </div>
                                    <h4 className="font-semibold text-slate-900 dark:text-slate-100">{player.nickname}</h4>
                                </div>
                                <div className="flex flex-wrap gap-2">
                                    {player.actions.map(action => (
                                        <button
                                            key={action.actionId}
                                            onClick={() => setSelectedAction({
                                                playerId: player.userId,
                                                actionId: action.actionId
                                            })}
                                            className={`px-4 py-2 rounded-lg text-sm font-medium transition-all duration-200 ${
                                                selectedAction?.playerId === player.userId &&
                                                selectedAction?.actionId === action.actionId
                                                    ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/30'
                                                    : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700'
                                            }`}
                                        >
                                            {getActionLabel(action.actionId)}
                                        </button>
                                    ))}
                                </div>
                            </div>
                        </div>
                    ))}

                    {selectedAction && (
                        <div
                            className="bg-gradient-to-br from-indigo-50 to-purple-50 dark:from-indigo-950/30 dark:to-purple-950/30 border border-indigo-200 dark:border-indigo-800/50 rounded-xl p-4 shadow-sm animate-in fade-in slide-in-from-bottom-2 duration-300">
                            <div className="mb-4">
                                <h4 className="font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                                    <span
                                        className="w-6 h-6 rounded-full bg-indigo-600 text-white flex items-center justify-center text-xs">✓</span>
                                    {t('actions.ui.selectTargetsFor', 'Select Target(s) for {action}', {action: getActionLabel(selectedAction.actionId)})}
                                </h4>
                            </div>
                            <div className="space-y-2 mb-4 max-h-48 overflow-y-auto">
                                {gameState?.players
                                    .filter(p => p.isAlive)
                                    .map(player => (
                                        <label key={player.id}
                                               className="flex items-center gap-3 p-2 rounded-lg hover:bg-white dark:hover:bg-slate-800/50 cursor-pointer transition-colors">
                                            <input
                                                type="checkbox"
                                                checked={selectedTargets.includes(String(player.userId))}
                                                onChange={(e) => {
                                                    if (e.target.checked) {
                                                        setSelectedTargets([...selectedTargets, String(player.userId)]);
                                                    } else {
                                                        setSelectedTargets(selectedTargets.filter(id => id !== String(player.userId)));
                                                    }
                                                }}
                                                className="w-5 h-5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500 cursor-pointer"
                                            />
                                            <img
                                                src={player.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png'}
                                                alt={player.name}
                                                className="w-6 h-6 rounded-full"
                                            />
                                            <span
                                                className="text-slate-700 dark:text-slate-200 font-medium">{player.name}</span>
                                            {selectedTargets.includes(String(player.userId)) && (
                                                <span
                                                    className="ml-auto text-xs bg-indigo-600 text-white px-2 py-1 rounded-full">{t('actions.ui.selected', 'selected')}</span>
                                            )}
                                        </label>
                                    ))}
                            </div>

                            <div className="flex gap-2">
                                <button
                                    onClick={handleSubmitAction}
                                    disabled={loading || submitted || selectedTargets.length === 0}
                                    className="flex-1 bg-indigo-600 hover:bg-indigo-700 disabled:bg-slate-300 dark:disabled:bg-slate-700 text-white font-semibold py-2.5 rounded-lg transition-all duration-200 flex items-center justify-center gap-2"
                                >
                                    {submitted ? (
                                        <>
                                            <CheckCircle2 className="w-4 h-4"/>
                                            {t('messages.submitted', 'Submitted')}
                                        </>
                                    ) : loading ? (
                                        <>
                                            <div
                                                className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                                            {t('actions.ui.submitting', 'Submitting...')}
                                        </>
                                    ) : (
                                        <>
                                            ✓ {t('actions.ui.submitAction', 'Submit Action')}
                                        </>
                                    )}
                                </button>
                                <button
                                    onClick={() => setSelectedAction(null)}
                                    className="px-4 py-2.5 rounded-lg border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 font-medium transition-colors"
                                >
                                    {t('common.cancel')}
                                </button>
                            </div>
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
