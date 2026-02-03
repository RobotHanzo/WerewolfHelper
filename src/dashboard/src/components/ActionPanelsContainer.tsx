import {GameState} from '@/types';
import {PlayerActionPanel} from './PlayerActionPanel';
import {JudgeActionPanel} from './JudgeActionPanel';
import {useTranslation} from '@/lib/i18n';

interface ActionPanelsContainerProps {
    guildId: string;
    gameState: GameState;
    userId: string;
    isJudge: boolean;
}

export function ActionPanelsContainer({
                                          guildId,
                                          gameState,
                                          userId,
                                          isJudge,
                                      }: ActionPanelsContainerProps) {
    const {t} = useTranslation();

    // Check if current phase allows actions
    const isNightPhase = gameState.currentState === 'NIGHT_PHASE';
    const isSpeechPhase = gameState.currentState === 'SPEECH_PHASE';
    const allowsActions = isNightPhase || isSpeechPhase;

    if (!allowsActions) {
        return null;
    }

    return (
        <div className="space-y-4 mt-6">
            {/* Divider */}
            <div className="border-t border-slate-200 dark:border-slate-800 pt-4">
                <h3 className="text-lg font-semibold text-slate-900 dark:text-slate-100 mb-4">
                    {isNightPhase ? t('actions.nightPhase') || 'Night Actions' : t('actions.daytimeActions') || 'Daytime Actions'}
                </h3>

                {/* Judge can always submit actions during these phases */}
                {isJudge && (
                    <div className="mb-6">
                        <div className="text-sm text-slate-600 dark:text-slate-400 mb-3 font-medium">
                            {t('speechManager.judgeManualOverride') || 'Judge Manual Override'}
                        </div>
                        <JudgeActionPanel guildId={guildId}/>
                    </div>
                )}

                {/* Players can submit actions during these phases */}
                {!isJudge && (
                    <div>
                        <div className="text-sm text-slate-600 dark:text-slate-400 mb-3 font-medium">
                            {t('actions.ui.yourAvailableActions', 'Your Available Actions')}
                        </div>
                        <PlayerActionPanel guildId={guildId} userId={userId}/>
                    </div>
                )}
            </div>
        </div>
    );
}
