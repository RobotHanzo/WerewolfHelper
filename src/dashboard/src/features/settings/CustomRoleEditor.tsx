import {useState} from 'react';
import {api} from '@/lib/api';
import {useTranslation} from '@/lib/i18n';

interface CustomActionDef {
    actionId: string;
    priority: number;
    timing: string;
    targetCount: number;
    usageLimit: number;
    requiresAliveTarget: boolean;
}

interface CustomRoleDef {
    name: string;
    camp: string;
    actions: CustomActionDef[];
    eventListeners: string[];
}

const TIMING_OPTIONS = ['NIGHT', 'DAY', 'ANYTIME'];
const CAMP_OPTIONS = ['WEREWOLF', 'GOD', 'VILLAGER'];

export function CustomRoleEditor({guildId, onClose}: { guildId: string; onClose: () => void }) {
    const {t} = useTranslation();
    const [roleName, setRoleName] = useState('');
    const [camp, setCamp] = useState('GOD');
    const [actions, setActions] = useState<CustomActionDef[]>([]);
    const [newAction, setNewAction] = useState<CustomActionDef>({
        actionId: '',
        priority: 500,
        timing: 'NIGHT',
        targetCount: 1,
        usageLimit: -1,
        requiresAliveTarget: true
    });
    const [warnings, setWarnings] = useState<string[]>([]);
    const [loading, setLoading] = useState(false);

    const addAction = () => {
        if (!newAction.actionId) {
            alert('Action ID is required');
            return;
        }

        // Check for warnings
        const newWarnings: string[] = [];
        if (camp === 'VILLAGER' && newAction.actionId.includes('KILL')) {
            newWarnings.push(`Warning: VILLAGER camp with killing action ${newAction.actionId}`);
        }

        setActions([...actions, newAction]);
        setNewAction({
            actionId: '',
            priority: 500,
            timing: 'NIGHT',
            targetCount: 1,
            usageLimit: -1,
            requiresAliveTarget: true
        });
        setWarnings(newWarnings);
    };

    const removeAction = (actionId: string) => {
        setActions(actions.filter(a => a.actionId !== actionId));
    };

    const handleSave = async () => {
        if (!roleName) {
            alert('Role name is required');
            return;
        }

        if (actions.length === 0) {
            alert('Role must have at least one action');
            return;
        }

        const roleDefinition: CustomRoleDef = {
            name: roleName,
            camp,
            actions,
            eventListeners: []
        };

        setLoading(true);
        try {
            const result = await api.saveCustomRole(guildId, roleDefinition) as {
                success: boolean;
                warnings?: string[];
                error?: string;
            };

            if (result.success) {
                if (result.warnings && result.warnings.length > 0) {
                    alert(`Role saved with warnings:\n${result.warnings.join('\n')}`);
                } else {
                    alert('Custom role saved successfully');
                }
                onClose();
            } else {
                alert(`Failed to save role: ${result.error}`);
            }
        } catch (error) {
            alert(`Error: ${error}`);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div className="bg-white dark:bg-slate-900 rounded-lg shadow-lg max-w-2xl w-full max-h-96 overflow-y-auto">
                <div
                    className="sticky top-0 bg-white dark:bg-slate-900 border-b border-gray-200 dark:border-slate-700 p-4 flex justify-between items-center">
                    <h2 className="text-xl font-semibold">{t('customRoleEditor.title') || 'Create Custom Role'}</h2>
                    <button
                        onClick={onClose}
                        className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                    >
                        ✕
                    </button>
                </div>

                <div className="p-6 space-y-6">
                    {/* Basic Info */}
                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium mb-2">Role Name</label>
                            <input
                                type="text"
                                value={roleName}
                                onChange={(e) => setRoleName(e.target.value)}
                                placeholder="e.g., 自定義角色"
                                className="w-full px-3 py-2 border border-gray-300 dark:border-slate-600 rounded bg-white dark:bg-slate-800"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium mb-2">Camp</label>
                            <select
                                value={camp}
                                onChange={(e) => setCamp(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 dark:border-slate-600 rounded bg-white dark:bg-slate-800"
                            >
                                {CAMP_OPTIONS.map(c => (
                                    <option key={c} value={c}>{c}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    {/* Warnings */}
                    {warnings.length > 0 && (
                        <div
                            className="bg-yellow-50 dark:bg-slate-800 border border-yellow-200 dark:border-yellow-900 rounded p-3">
                            <div className="text-sm text-yellow-800 dark:text-yellow-200">
                                {warnings.map((w, i) => (
                                    <div key={i}>{w}</div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Actions List */}
                    <div>
                        <label className="block text-sm font-medium mb-2">Actions</label>
                        <div className="space-y-2 mb-4 max-h-40 overflow-y-auto">
                            {actions.map(action => (
                                <div
                                    key={action.actionId}
                                    className="flex items-center justify-between bg-gray-100 dark:bg-slate-800 p-3 rounded"
                                >
                                    <div>
                                        <div className="font-medium">{action.actionId}</div>
                                        <div className="text-xs text-gray-600 dark:text-gray-400">
                                            {action.timing} | Priority: {action.priority} |
                                            Uses: {action.usageLimit === -1 ? '∞' : action.usageLimit}
                                        </div>
                                    </div>
                                    <button
                                        onClick={() => removeAction(action.actionId)}
                                        className="text-red-500 hover:text-red-700"
                                    >
                                        ✕
                                    </button>
                                </div>
                            ))}
                        </div>

                        {/* Add Action Form */}
                        <div className="bg-gray-50 dark:bg-slate-800 p-4 rounded space-y-3">
                            <input
                                type="text"
                                value={newAction.actionId}
                                onChange={(e) => setNewAction({...newAction, actionId: e.target.value})}
                                placeholder="Action ID (e.g., CUSTOM_KILL)"
                                className="w-full px-3 py-2 border border-gray-300 dark:border-slate-600 rounded bg-white dark:bg-slate-700 text-sm"
                            />

                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-xs font-medium mb-1">Timing</label>
                                    <select
                                        value={newAction.timing}
                                        onChange={(e) => setNewAction({...newAction, timing: e.target.value})}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-slate-600 rounded bg-white dark:bg-slate-700 text-sm"
                                    >
                                        {TIMING_OPTIONS.map(timing => (
                                            <option key={timing} value={timing}>{timing}</option>
                                        ))}
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-xs font-medium mb-1">Priority</label>
                                    <input
                                        type="number"
                                        value={newAction.priority}
                                        onChange={(e) => setNewAction({
                                            ...newAction,
                                            priority: parseInt(e.target.value)
                                        })}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-slate-600 rounded bg-white dark:bg-slate-700 text-sm"
                                    />
                                </div>

                                <div>
                                    <label className="block text-xs font-medium mb-1">Target Count</label>
                                    <input
                                        type="number"
                                        value={newAction.targetCount}
                                        onChange={(e) => setNewAction({
                                            ...newAction,
                                            targetCount: parseInt(e.target.value)
                                        })}
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-slate-600 rounded bg-white dark:bg-slate-700 text-sm"
                                    />
                                </div>

                                <div>
                                    <label className="block text-xs font-medium mb-1">Usage Limit</label>
                                    <input
                                        type="number"
                                        value={newAction.usageLimit}
                                        onChange={(e) => setNewAction({
                                            ...newAction,
                                            usageLimit: parseInt(e.target.value)
                                        })}
                                        placeholder="-1 for unlimited"
                                        className="w-full px-3 py-2 border border-gray-300 dark:border-slate-600 rounded bg-white dark:bg-slate-700 text-sm"
                                    />
                                </div>
                            </div>

                            <label className="flex items-center gap-2 text-sm">
                                <input
                                    type="checkbox"
                                    checked={newAction.requiresAliveTarget}
                                    onChange={(e) => setNewAction({
                                        ...newAction,
                                        requiresAliveTarget: e.target.checked
                                    })}
                                    className="w-4 h-4"
                                />
                                Requires Alive Target
                            </label>

                            <button
                                onClick={addAction}
                                className="w-full bg-blue-500 hover:bg-blue-600 text-white font-medium py-2 rounded text-sm transition"
                            >
                                Add Action
                            </button>
                        </div>
                    </div>

                    {/* Save Button */}
                    <div className="flex gap-3 justify-end pt-4 border-t border-gray-200 dark:border-slate-700">
                        <button
                            onClick={onClose}
                            className="px-4 py-2 border border-gray-300 dark:border-slate-600 rounded hover:bg-gray-100 dark:hover:bg-slate-800 transition"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleSave}
                            disabled={loading}
                            className="px-4 py-2 bg-green-500 hover:bg-green-600 text-white rounded disabled:opacity-50 transition"
                        >
                            {loading ? 'Saving...' : 'Save Role'}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
