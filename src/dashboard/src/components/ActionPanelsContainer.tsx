import {GameState} from '@/types';
import {useTranslation} from '@/lib/i18n';

interface ActionPanelsContainerProps {
    gameState: GameState;
}

export function ActionPanelsContainer({
                                          gameState,
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
            </div>
        </div>
    );
}
