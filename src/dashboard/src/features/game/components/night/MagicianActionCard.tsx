import React from 'react';
import { useTranslation } from '@/lib/i18n';
import { ArrowLeftRight, Check, Brain } from 'lucide-react';
import { Player, RoleActionInstance } from '@/api/types.gen';
import { getRoleConfig } from '@/constants/gameData';
import { DiscordAvatar, DiscordName } from '@/components/DiscordUser';
import { EnrichedActionStatus } from './types';

interface MagicianActionCardProps {
  status: EnrichedActionStatus | RoleActionInstance;
  index?: number;
  players: Player[];
  guildId?: string;
  variant?: 'default' | 'large';
}

export const MagicianActionCard: React.FC<MagicianActionCardProps> = ({
  status,
  index = 0,
  players,
  guildId,
  variant = 'default',
}) => {
  const { t } = useTranslation();
  
  // Handle potentially different shape of status object
  const actorRole = 'actorRole' in status ? status.actorRole : 'MAGICIAN';
  const roleConfig = getRoleConfig(actorRole);
  const RoleIcon = roleConfig.icon || Brain;
  const roleName = t('roles.magician');

  const isActing = status.status === 'ACTING';
  const isSkipped = status.status === 'SKIPPED';
  const isProcessed = status.status === 'PROCESSED';
  const isSubmitted = status.status === 'SUBMITTED' || isProcessed;

  const t1Id = status.targets?.[0];
  const t2Id = status.targets?.[1];
  const t1 = t1Id && t1Id !== -1 ? players.find((p) => p.id === Number(t1Id)) : null;
  const t2 = t2Id && t2Id !== -1 ? players.find((p) => p.id === Number(t2Id)) : null;
  
  const actorId = 'playerUserId' in status ? status.playerUserId : (status as RoleActionInstance).actor;
  const actor = players.find(p => p.id === Number(actorId));

  const isLarge = variant === 'large';

  // --- Sub-components ---

  const TargetDisplay = ({ target, label }: { target: Player | null | undefined, label: string }) => (
    <div className={`flex flex-col items-center gap-2 ${isLarge ? 'z-10' : 'flex-1'}`}>
      <div 
        className={`
          rounded-full p-1 bg-white dark:bg-slate-800 relative
          ${isLarge 
            ? 'w-24 h-24 shadow-xl ring-4 ring-slate-100 dark:ring-slate-800' 
            : `w-10 h-10 border-2 overflow-hidden flex items-center justify-center ${target ? 'border-indigo-400' : 'border-slate-300 dark:border-slate-700 dashed'}`
          }
        `}
      >
        {target ? (
          <div className="w-full h-full rounded-full overflow-hidden relative">
            <DiscordAvatar
              userId={String(target.userId)}
              guildId={guildId}
              avatarClassName="w-full h-full object-cover"
            />
          </div>
        ) : (
          <div className="w-full h-full rounded-full bg-slate-100 dark:bg-slate-800 flex items-center justify-center">
            <div className={`${isLarge ? 'text-4xl' : 'text-xs'} text-slate-300 dark:text-slate-600 font-thin`}>
              ?
            </div>
          </div>
        )}
        {isLarge && (
          <div className="absolute -bottom-2 left-1/2 -translate-x-1/2 bg-slate-800 text-white text-[10px] font-bold px-2 py-0.5 rounded-full uppercase">
            {label}
          </div>
        )}
      </div>
      <div className="text-center">
        {isLarge ? (
          <p className="font-bold text-slate-900 dark:text-white text-lg">
            {target ? (
              <DiscordName
                userId={String(target.userId)}
                guildId={guildId}
                fallbackName={target.nickname}
              />
            ) : (
              <span className="text-slate-400 italic">
                {t('nightStatus.waiting')}
              </span>
            )}
          </p>
        ) : (
          <span className="text-[10px] font-bold text-slate-600 dark:text-slate-300 truncate max-w-[60px] block">
            {target ? (
              <DiscordName
                userId={String(target.userId)}
                guildId={guildId}
                fallbackName={target.nickname}
              />
            ) : (
              t('nightStatus.waiting')
            )}
          </span>
        )}
      </div>
    </div>
  );

  const StatusBadge = () => (
    <div
      className={`
        px-2.5 py-1 rounded-full text-[10px] font-bold tracking-wide uppercase border flex items-center gap-1
        ${isLarge ? 'px-4 py-1.5 text-sm rounded-full tracking-wider' : 'rounded'} 
        ${
          isSubmitted
            ? 'bg-green-100 text-green-700 border-green-200 dark:bg-green-500/10 dark:text-green-400 dark:border-green-500/30'
            : isActing 
              ? 'bg-purple-500/10 text-purple-500 border-purple-500/20 animate-pulse'
              : 'bg-amber-100 text-amber-700 border-amber-200 dark:bg-amber-500/10 dark:text-amber-400 dark:border-amber-500/30'
        }
      `}
    >
      {isProcessed && <Check className="w-3 h-3" />}
      {status.status
        ? t(`nightStatus.${status.status.toLowerCase()}`)
        : status.status}
    </div>
  );

  const SwapIcon = () => (
    <div className={`flex items-center justify-center ${isLarge ? 'z-10 bg-white dark:bg-slate-900 p-4 rounded-full shadow-lg border border-slate-100 dark:border-slate-800' : 'px-2'}`}>
      {isLarge ? (
        <ArrowLeftRight className="w-8 h-8 text-purple-500" />
      ) : (
        <div className="w-8 h-8 rounded-full bg-white dark:bg-slate-800 shadow-sm border border-slate-200 dark:border-slate-700 flex items-center justify-center">
           <ArrowLeftRight className="w-4 h-4 text-purple-500" />
        </div>
      )}
    </div>
  );

  const PerformerInfo = () => (
     <div className={`flex items-center justify-end gap-2 opacity-60 ${isLarge ? 'mt-12 justify-center' : 'mt-3'}`}>
       {isLarge ? (
          <div className="flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400 bg-slate-50 dark:bg-slate-800/50 px-3 py-1.5 rounded-full">
            <span className="uppercase text-[10px] font-bold tracking-wider">{t('nightStatus.actor')}:</span>
            <span>{actor?.nickname || 'Unknown'}</span>
            <DiscordAvatar userId={String(actor?.userId)} guildId={guildId} avatarClassName="w-4 h-4 rounded-full" />
         </div>
       ) : (
         <>
          <span className="text-[9px] uppercase font-bold text-slate-400">{t('nightStatus.actor')}</span>
          <div className="w-5 h-5 rounded-full border border-slate-200 dark:border-slate-700 overflow-hidden">
             <DiscordAvatar userId={String(actorId)} guildId={guildId} avatarClassName="w-full h-full object-cover" />
          </div>
         </>
       )}
    </div>
  );

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
            {isLarge ? roleName : actorRole}
          </h4>
          <span
            className={
              isLarge
                ? "text-slate-500 dark:text-slate-400 font-medium"
                : `text-[10px] font-bold mt-1 px-1.5 py-0.5 rounded inline-block`
            }
            style={!isLarge ? {
              backgroundColor: `${roleConfig.color}20`,
              color: roleConfig.color,
            } : undefined}
          >
            {t('actions.labels.MAGICIAN_SWAP') || 'Swap'}
          </span>
        </div>
      </div>
      <StatusBadge />
    </div>
  );

  const Body = () => (
    <div className={
      isLarge 
        ? "flex flex-col md:flex-row items-center justify-center gap-8 md:gap-12 relative"
        : "bg-slate-50 dark:bg-black/20 rounded-lg p-4 relative min-h-[100px] border border-slate-100 dark:border-slate-700 flex items-center justify-between"
    }>
      <TargetDisplay target={t1} label={t('nightStatus.target') + " 1"} />
      <SwapIcon />
      <TargetDisplay target={t2} label={t('nightStatus.target') + " 2"} />
    </div>
  );

  // --- Render ---

  const content = (
    <>
      <Header />
      <Body />
      <PerformerInfo />
    </>
  );

  if (isLarge) {
    return (
      <div className="max-w-4xl mx-auto mt-10 animate-in fade-in zoom-in-95 duration-500 p-4">
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
