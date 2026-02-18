import React from 'react';
import { useTranslation } from '@/lib/i18n';
import { ArrowRight, Ban, Check, FastForward, User } from 'lucide-react';
import { getActionConfig, getRoleConfig } from '@/constants/gameData';
import { DiscordAvatar, DiscordName } from '@/components/DiscordUser';
import { EnrichedActionStatus } from './types';
import { Player, RoleActionInstance } from '@/api/types.gen';

interface StandardActionCardProps {
  status: EnrichedActionStatus | RoleActionInstance;
  index?: number;
  guildId?: string;
  variant?: 'default' | 'large';
  roleId?: string;
  players?: Player[]; // Needed for Large variant to lookup actor/target if status is RoleActionInstance
}

export const StandardActionCard: React.FC<StandardActionCardProps> = ({
  status,
  index = 0,
  guildId,
  variant = 'default',
  roleId,
  players = [],
}) => {
  const { t } = useTranslation();

  // Resolve data based on inputs
  const actorRole = roleId || ('actorRole' in status ? status.actorRole : 'UNKNOWN');
  const roleConfig = getRoleConfig(actorRole);
  const actionDefinitionId = status.actionDefinitionId;
  const actionConfig = actionDefinitionId ? getActionConfig(actionDefinitionId) : null;

  const RoleIcon = roleConfig.icon;
  const isActing = status.status === 'ACTING';
  // Use targets array to determine if skipped (targetId === -1)
  const targetId = status.targets?.[0];
  const isSkipped = status.status === 'SKIPPED' || (targetId === -1);
  const isProcessed = status.status === 'PROCESSED';
  const isSubmitted = status.status === 'SUBMITTED' || isProcessed;

  const actorId = 'playerUserId' in status ? status.playerUserId : (status as RoleActionInstance).actor;
  const targetUserId = 'targetUserId' in status ? status.targetUserId : (targetId !== -1 ? targetId : undefined);

  const actor = players.find(p => p.id === Number(actorId));
  const targetPlayer = players.find(p => p.id === Number(targetUserId));

  // Fallbacks for names if player object not found (mainly for default variant where names might be pre-enriched)
  const actorName = 'playerName' in status ? status.playerName : actor?.nickname;
  const targetName = 'targetName' in status ? status.targetName : targetPlayer?.nickname;
  const targetRole = 'targetRole' in status ? status.targetRole : undefined;

  const isLarge = variant === 'large';
  const ActionIcon = actionConfig?.icon || ArrowRight;

  // --- Sub-components ---

  const Header = () => (
    <div className={isLarge ? "flex flex-col md:flex-row items-center justify-between gap-6 mb-12" : "flex justify-between items-start mb-6"}>
      <div className={`flex items-center ${isLarge ? 'gap-5' : 'gap-3'}`}>
        <div
          className={`flex items-center justify-center ${
            isLarge
              ? `p-4 rounded-2xl shadow-lg`
              : `h-10 w-10 rounded-lg`
          }`}
          style={{
            backgroundColor: isSkipped ? undefined : `${roleConfig.color}20`,
            color: isSkipped ? undefined : roleConfig.color,
          }}
        >
          <RoleIcon className={isLarge ? "w-8 h-8" : "w-5 h-5"} />
        </div>
        <div>
          <h4 className={`font-bold text-slate-900 dark:text-white ${isLarge ? 'text-3xl tracking-tight' : ''}`}>
             {t(roleConfig.translationKey || `roles.${actorRole.toLowerCase()}`) || actorRole}
          </h4>
          <div className="flex flex-col">
            {isLarge ? (
               <p className="text-slate-500 dark:text-slate-400 font-medium">
                {actionDefinitionId
                  ? t(`actions.labels.${actionDefinitionId}`)
                  : t('features.action')}
              </p>
            ) : (
                (status as any).actionName && (
                  <span
                    className="text-[10px] font-bold mt-1 px-1.5 py-0.5 rounded"
                    style={{
                      backgroundColor: `${roleConfig.color}20`,
                      color: roleConfig.color,
                    }}
                  >
                    {(status as any).actionName}
                  </span>
                )
            )}
          </div>
        </div>
      </div>

      <div
        className={`px-2.5 py-1 rounded-full text-[10px] font-bold tracking-wide uppercase border flex items-center gap-1
          ${isLarge ? 'px-4 py-1.5 text-sm rounded-full tracking-wider' : 'rounded'}
          ${
              isSubmitted
                ? 'bg-green-100 text-green-700 border-green-200 dark:bg-green-500/10 dark:text-green-400 dark:border-green-500/30'
                : isActing
                ? 'bg-[#3211d4]/10 dark:bg-[#3211d4]/20 text-[#3211d4] dark:text-white border-[#3211d4]/30 dark:border-[#3211d4]/50 animate-pulse'
                : isSkipped
                ? 'bg-amber-100 text-amber-700 border-amber-200 dark:bg-amber-500/10 dark:text-amber-400 dark:border-amber-500/30'
                : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-600'
        }`}
      >
        {isProcessed ? (
           <Check className="w-3 h-3" />
        ) : isLarge && !isProcessed ? (
            <div className={`w-2 h-2 rounded-full ${isSubmitted ? 'bg-green-500' : 'bg-amber-500 animate-pulse'}`} />
        ) : null}

        {status.status
          ? t(`nightStatus.${status.status.toLowerCase()}`)
          : String(status.status)}
      </div>
    </div>
  );

  const ActorDisplay = () => (
     <div className={`relative z-10 ${isLarge ? 'flex flex-col items-center gap-4 w-full md:w-auto' : 'text-center'}`}>
        <div
          className={`
            rounded-full relative
            ${isLarge
               ? 'w-28 h-28 p-1 bg-white dark:bg-slate-800 shadow-xl ring-4 ring-slate-100 dark:ring-slate-800 group transition-transform hover:scale-105 duration-300'
               : 'w-12 h-12 border-2 p-0.5 mx-auto mb-2'
            }
          `}
          style={!isLarge ? {
            borderColor: isSkipped ? '#f59e0b80' : `${roleConfig.color}80`,
          } : undefined}
        >
          {actorId ? (
            <div className="relative w-full h-full rounded-full overflow-hidden">
              <DiscordAvatar
                userId={String(actorId)}
                guildId={guildId}
                avatarClassName="w-full h-full rounded-full object-cover"
              />
               {isLarge && <div className="absolute inset-0 ring-1 ring-inset ring-black/10 rounded-full" />}
            </div>
          ) : (
            <div className="w-full h-full rounded-full bg-slate-800 flex items-center justify-center">
              <User className="text-slate-600 w-6 h-6" />
            </div>
          )}
           {isLarge && (
              <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-slate-800 text-white text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider shadow-sm">
                {t('nightStatus.actor')}
              </div>
           )}
        </div>
        <span className={`${isLarge ? 'font-bold text-slate-900 dark:text-white text-lg leading-tight' : 'text-[10px] font-bold text-slate-400 truncate max-w-[60px] block'}`}>
          <DiscordName
            userId={String(actorId || '')}
            guildId={guildId}
            fallbackName={actorName}
          />
        </span>
      </div>
  );

  const ActionArrow = () => (
     <>
        {isLarge ? (
            <div className="flex flex-col items-center justify-center z-10 bg-white dark:bg-slate-900 px-4 py-2 rounded-full shadow-sm border border-slate-100 dark:border-slate-800">
                <ActionIcon
                  className={`w-8 h-8 ${isSubmitted ? 'text-green-500' : 'text-slate-300 dark:text-slate-600'}`}
                />
            </div>
        ) : (
            <>
              <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-16 h-[2px] bg-gradient-to-r from-white/10 to-white/30 z-0"></div>
              <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-10 bg-white dark:bg-slate-900 p-1 rounded-full border border-slate-200 dark:border-slate-800 shadow-sm">
                {isSkipped ? (
                  <Ban className="text-amber-500 w-4 h-4" />
                ) : actionConfig ? (
                  <actionConfig.icon className="text-slate-500 w-4 h-4" />
                ) : (
                  <ArrowRight className="text-slate-500 w-4 h-4" />
                )}
              </div>
            </>
        )}
     </>
  );

  const TargetDisplay = () => (
    <div className={`relative z-10 ${isLarge ? 'flex flex-col items-center gap-4 w-full md:w-auto' : 'text-center'}`}>
        <div
          className={`
             rounded-full relative
            ${isLarge
                ? `w-28 h-28 p-1 shadow-xl transition-all duration-300 flex items-center justify-center ${
                    isSkipped
                      ? 'bg-amber-50 border-4 border-amber-200 ring-4 ring-amber-100 dark:bg-amber-900/20 dark:border-amber-700 dark:ring-amber-900/10'
                      : targetUserId
                        ? 'bg-white dark:bg-slate-800 border-4 border-white dark:border-slate-700 ring-4 ring-slate-100 dark:ring-slate-800'
                        : 'bg-slate-50 dark:bg-slate-800/50 border-4 border-dashed border-slate-300 dark:border-slate-700'
                  }`
                : `w-12 h-12 border-2 p-0.5 mx-auto mb-2 group-hover:border-white/50 transition-colors duration-300 ${
                    isSkipped ? 'border-amber-500/50' : 'border-slate-600'
                  }`
            }
          `}
        >
          {isSkipped ? (
             isLarge ? <Ban className="w-12 h-12 text-amber-500" /> : (
                <div className="w-full h-full rounded-full bg-amber-500/10 flex items-center justify-center">
                  <FastForward className="text-amber-500 w-6 h-6" />
                </div>
             )
          ) : targetUserId ? (
              <div className={`w-full h-full rounded-full ${isLarge ? 'overflow-hidden relative' : 'object-cover'}`}>
                 <DiscordAvatar
                    userId={String(targetUserId)}
                    guildId={guildId}
                    avatarClassName="w-full h-full rounded-full object-cover"
                  />
                  {isLarge && <div className="absolute inset-0 ring-1 ring-inset ring-black/10 rounded-full" />}
              </div>
          ) : (
            <div className={`w-full h-full rounded-full ${isLarge ? 'flex items-center justify-center' : 'bg-slate-800 flex items-center justify-center'}`}>
               {isLarge ? (
                  <div className="text-5xl text-slate-300 dark:text-slate-600 font-thin animate-pulse">?</div>
               ) : (
                  <User className="text-slate-600 w-6 h-6" />
               )}
            </div>
          )}

          {/* Status Indicators */}
          {!isLarge && isSubmitted && (
            <div className="absolute -top-1 -right-1 h-5 w-5 bg-white dark:bg-slate-900 rounded-full flex items-center justify-center border border-slate-200 dark:border-slate-800">
              <Check className="text-green-500 w-3 h-3" />
            </div>
          )}

          {isLarge && (!!targetUserId || isSkipped) && (
              <div
                className={`absolute -bottom-2 left-1/2 -translate-x-1/2 text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider shadow-sm text-white ${isSkipped ? 'bg-amber-600' : 'bg-slate-800'}`}
              >
                {t('nightStatus.target')}
              </div>
          )}
        </div>

        <div className={`flex flex-col ${isLarge ? 'text-center min-h-[3rem] justify-center' : ''}`}>
          <span className={`${isLarge ? 'font-bold text-slate-900 dark:text-white text-lg leading-tight' : 'text-xs font-bold text-slate-900 dark:text-white truncate max-w-[80px]'}`}>
            {isSkipped ? (
              <span className="text-amber-500 italic">
                {t('nightStatus.skipped')}
              </span>
            ) : targetUserId ? (
              <DiscordName
                userId={String(targetUserId)}
                guildId={guildId}
                fallbackName={targetName || ''}
              />
            ) : (
               isLarge ? (
                  <span className="text-slate-400 italic">{t('nightStatus.waiting')}</span>
               ) : (
                  targetName ||
                  (isActing
                    ? t('nightStatus.thinking')
                    : t('nightStatus.waiting'))
               )
            )}
          </span>
          {!isLarge && targetRole && (
              <span className="text-[10px] text-[#3211d4] font-bold uppercase">
                {targetRole}
              </span>
          )}
        </div>
      </div>
  );

  // --- Render ---

  const content = (
      <>
        <Header />
         <div className={
            isLarge
             ? "flex flex-col md:flex-row items-center justify-between gap-8 relative"
             : "flex items-center justify-between bg-slate-50 dark:bg-black/20 rounded-lg p-4 relative min-h-[100px] border border-slate-100 dark:border-slate-700"
         }>
             {isLarge && <div className="hidden md:block absolute top-1/2 left-20 right-20 h-0.5 bg-slate-200 dark:bg-slate-700 -z-10" />}
             <ActorDisplay />
             <ActionArrow />
             <TargetDisplay />
         </div>
      </>
  );

  if (isLarge) {
      return (
          <div className="max-w-3xl mx-auto mt-10 animate-in fade-in zoom-in-95 duration-500 p-4">
            <div
              className="relative overflow-hidden rounded-3xl shadow-2xl bg-white dark:bg-slate-900 border-2"
              style={{ borderColor: roleConfig.color }}
            >
              <div
                className="absolute top-0 left-0 right-0 h-32 opacity-10"
                style={{
                  background: `linear-gradient(to bottom, ${roleConfig.color}, transparent)`,
                }}
              />

              <div className="relative p-8 md:p-12">
                 {content}
              </div>
            </div>
          </div>
      );
  }

  return (
    <div
      key={`${'actor' in status ? status.actor : (status as any).actor}-${index}`}
      className={`bg-white dark:bg-slate-900 rounded-xl border-l-4 border-t border-r border-b border-slate-200 dark:border-slate-800 overflow-hidden group transition-all duration-300 animate-in fade-in slide-in-from-left-4 fill-mode-both ${
        isActing ? 'ring-1' : 'shadow-lg'
      } ${isSkipped ? 'opacity-75 grayscale border-l-slate-500' : ''}`}
      style={{
        animationDelay: `${250 + index * 100}ms`,
        borderLeftColor: isSkipped ? undefined : roleConfig.color,
        boxShadow: isActing ? `0 0 20px ${roleConfig.color}40` : undefined,
      }}
    >
      <div className="p-5 relative z-10">
        {content}
      </div>
    </div>
  );
};
