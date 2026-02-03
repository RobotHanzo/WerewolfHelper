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

export function PlayerActionPanel({guildId, userId}: { guildId: string; userId: string }) {
    const [availableActions, setAvailableActions] = useState<AvailableAction[]>([]);
    const [selectedAction, setSelectedAction] = useState<AvailableAction | null>(null);
    const [selectedTargets, setSelectedTargets] = useState<string[]>([]);
    const [loading, setLoading] = useState(false);
    const [submitted, setSubmitted] = useState(false);
    const {gameState} = useGameState(guildId, null);
    const {t} = useTranslation();
    const toast = useToast();

    // Get current player's action submission status
    const currentPlayer = gameState?.players?.find(p => p.userId?.toString() === userId);
    const playerAlreadySubmitted = currentPlayer?.actionSubmitted ?? false;

    useEffect(() => {
        loadAvailableActions();
    }, [guildId, userId]);

    const loadAvailableActions = async () => {
        try {
            const response = await api.getAvailableActions(guildId, userId) as any;
            if (response?.actions && Array.isArray(response.actions)) {
                setAvailableActions(response.actions);
            }
        } catch (error) {
            console.error('Failed to load available actions:', error);
        }
    };

    const getActionLabel = (actionId: string) => t(`actions.labels.${actionId}`, actionId);
    const getTimingLabel = (timing: string) => t(`actions.timing.${timing}`, timing);

    const translateActionError = (error: string) => {
        if (!error) return error;
        if (error.startsWith('Actor does not have role ')) {
            const roleName = error.replace('Actor does not have role ', '');
            return t('actions.errors.actorMissingRole', 'Actor does not have role {role}', {role: roleName});
        }
        return t(`actions.errors.${error}`, error);
    };

    const handleSubmitAction = async () => {
        if (!selectedAction || selectedTargets.length !== selectedAction.targetCount) {
            toast.error(t('actions.ui.selectTargetsCount', 'Please select {count} target(s)', {count: String(selectedAction?.targetCount)}));
            return;
        }

        setLoading(true);
        setSubmitted(false);
        try {
            const result = await api.submitRoleAction(
                guildId,
                selectedAction.actionId,
                userId,
                selectedTargets,
                'PLAYER'
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

    if (availableActions.length === 0) {
        return null;
    }

    // Show locked message if player has already submitted
    if (playerAlreadySubmitted) {
        return (
            <div className="bg-white dark:bg-slate-900 rounded-lg shadow-md p-4 border-2 border-green-500">
                <h3 className="text-lg font-semibold mb-2 text-green-600 dark:text-green-400 flex items-center gap-2">
                    <CheckCircle2 className="w-5 h-5"/>
                    {t('actions.ui.actionSubmitted', 'Action Submitted')}
                </h3>
                <p className="text-slate-600 dark:text-slate-300">
                    {t('actions.ui.waitForNextPhase', 'You have already submitted an action for this phase. Please wait for the next phase.')}
                </p>
            </div>
        );
    }

    return (
        <div className="bg-white dark:bg-slate-900 rounded-lg shadow-md p-4">
            <h3 className="text-lg font-semibold mb-4">{t('actions.ui.availableNightActions', 'Available Night Actions')}</h3>

            <div className="space-y-3">
                {availableActions.map(action => (
                    <div
                        key={action.actionId}
                        className={`border-2 rounded p-3 cursor-pointer transition ${
                            selectedAction?.actionId === action.actionId
                                ? 'border-blue-500 bg-blue-50 dark:bg-slate-800'
                                : 'border-gray-200 dark:border-slate-700 hover:border-gray-300 dark:hover:border-slate-600'
                        }`}
                        onClick={() => {
                            setSelectedAction(action);
                            setSelectedTargets([]);
                        }}
                    >
                        <div className="flex justify-between items-start">
                            <div>
                                <div className="font-medium">{getActionLabel(action.actionId)}</div>
                                <div className="text-sm text-gray-600 dark:text-gray-400">
                                    {action.roleName}
                                </div>
                            </div>
                            <div className="text-xs bg-gray-200 dark:bg-slate-700 px-2 py-1 rounded">
                                {getTimingLabel(action.timing)}
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {selectedAction && (
                <div
                    className="mt-6 bg-blue-50 dark:bg-slate-800 border border-blue-200 dark:border-slate-700 rounded p-4">
                    <div className="font-medium mb-4">
                        {t('actions.ui.selectTargetsCountForAction', 'Select {count} target(s) for {action}', {
                            count: String(selectedAction.targetCount),
                            action: getActionLabel(selectedAction.actionId)
                        })}
                    </div>

                    <div className="space-y-2 max-h-40 overflow-y-auto">
                        {gameState?.players
                            .filter(p => {
                                if (selectedAction.requiresAliveTarget) {
                                    return p.isAlive;
                                }
                                return true;
                            })
                            .map(player => (
                                <label key={player.id} className="flex items-center gap-2 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={selectedTargets.includes(player.userId?.toString() || '')}
                                        onChange={(e) => {
                                            if (e.target.checked) {
                                                if (selectedTargets.length < selectedAction.targetCount) {
                                                    setSelectedTargets([...selectedTargets, player.userId?.toString() || '']);
                                                }
                                            } else {
                                                setSelectedTargets(selectedTargets.filter(id => id !== player.userId?.toString()));
                                            }
                                        }}
                                        disabled={
                                            selectedTargets.length >= selectedAction.targetCount &&
                                            !selectedTargets.includes(player.userId?.toString() || '')
                                        }
                                        className="w-4 h-4 disabled:opacity-50"
                                    />
                                    <span>{player.name}</span>
                                </label>
                            ))}
                    </div>

                    <button
                        onClick={handleSubmitAction}
                        disabled={loading || submitted || selectedTargets.length !== selectedAction.targetCount}
                        className="mt-4 w-full bg-green-500 hover:bg-green-600 text-white font-medium py-2 rounded disabled:opacity-50 transition flex items-center justify-center gap-2"
                    >
                        {submitted ? (
                            <>
                                <CheckCircle2 className="w-4 h-4"/>
                                {t('messages.submitted', 'Submitted')}
                            </>
                        ) : loading ? (
                            <span>{t('actions.ui.submitting', 'Submitting...')}</span>
                        ) : (
                            <span>{t('actions.ui.submitAction', 'Submit Action')}</span>
                        )}
                    </button>
                </div>
            )}
        </div>
    );
}
