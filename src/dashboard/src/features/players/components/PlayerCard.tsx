import React, { useEffect, useState } from 'react';
import { ArrowLeftRight, HeartPulse, Lock, Settings, Shield, Skull, Unlock } from 'lucide-react';
import { Player } from '@/api/types.gen';
import { useTranslation } from '@/lib/i18n';
import { DiscordAvatar, DiscordName } from '@/components/DiscordUser';

interface PlayerCardProps {
  player: Player;
  onAction: (id: number, action: string) => void;
  readonly?: boolean;
}

export const PlayerCard: React.FC<PlayerCardProps> = ({ player, onAction, readonly = false }) => {
  const { t } = useTranslation();
  const cardStyle =
    'bg-slate-100 dark:bg-slate-800/50 rounded-xl border border-slate-300 dark:border-slate-700/50 hover:border-indigo-400 dark:hover:border-indigo-500/50 transition-all duration-200 overflow-hidden relative group';

  const [animate, setAnimate] = useState(false);
  const [prevRoleString, setPrevRoleString] = useState(JSON.stringify(player.roles));
  const [swapAnim, setSwapAnim] = useState<Record<number, string>>({});

  const [showLock, setShowLock] = useState(false);
  const [fading, setFading] = useState(false);
  const [prevLocked, setPrevLocked] = useState(player.rolePositionLocked);

  useEffect(() => {
    const currentRoleString = JSON.stringify(player.roles);
    if (currentRoleString !== prevRoleString) {
      let isSwap = false;
      try {
        const oldRoles = JSON.parse(prevRoleString);
        const newRoles = player.roles;
        if (Array.isArray(oldRoles) && oldRoles.length === 2 && newRoles.length === 2) {
          if (oldRoles[0] === newRoles[1] && oldRoles[1] === newRoles[0]) {
            isSwap = true;
          }
        }
      } catch (e) {
        /* ignore */
      }

      if (isSwap) {
        setAnimate(false);
        setSwapAnim({ 0: 'animate-slide-left-in', 1: 'animate-slide-right-in' });
        const t = setTimeout(() => setSwapAnim({}), 400);
        setPrevRoleString(currentRoleString);
        return () => clearTimeout(t);
      } else {
        setAnimate(true);
        setPrevRoleString(currentRoleString);
        const t = setTimeout(() => setAnimate(false), 500);
        return () => clearTimeout(t);
      }
    }
  }, [player.roles, prevRoleString]);

  useEffect(() => {
    // Check if transitioning from unlocked to locked
    if (player.rolePositionLocked === true && prevLocked === false) {
      setShowLock(true);
      setFading(false);
      // Start fade out
      const t1 = setTimeout(() => setFading(true), 100);
      const t2 = setTimeout(() => setShowLock(false), 2000);
      return () => {
        clearTimeout(t1);
        clearTimeout(t2);
      };
    }
    setPrevLocked(player.rolePositionLocked);
  }, [player.rolePositionLocked, prevLocked]);

  // handleKillClick removed, using onAction directly

  return (
    <div className={`${cardStyle} ${!player.alive ? 'opacity-60 grayscale' : ''} flex flex-col`}>
      <div className="p-4 flex-1">
        {/* Header */}
        <div className="flex items-start justify-between mb-2">
          <div className="flex items-center gap-3">
            <div className="relative">
              {player.userId ? (
                <DiscordAvatar
                  userId={player.userId}
                  avatarClassName="w-12 h-12 rounded-full border-2 border-slate-400 dark:border-slate-600 bg-slate-300 dark:bg-slate-700 object-cover"
                />
              ) : (
                <div className="w-12 h-12 rounded-full border-2 border-slate-400 dark:border-slate-600 bg-slate-300 dark:bg-slate-700 flex items-center justify-center">
                  <Settings className="w-6 h-6 text-slate-500 dark:text-slate-400" />
                </div>
              )}
              {player.police && (
                <div
                  className="absolute -bottom-1 -right-1 bg-green-600 text-white rounded-full p-0.5 border-2 border-slate-200 dark:border-slate-800"
                  title={t('status.sheriff')}
                >
                  <Shield className="w-3 h-3" />
                </div>
              )}
              {player.jinBaoBao && (
                <div
                  className="absolute -top-1 -right-1 bg-pink-500 text-white rounded-full p-0.5 border-2 border-slate-200 dark:border-slate-800"
                  title={t('status.jinBaoBao')}
                >
                  <HeartPulse className="w-3 h-3" />
                </div>
              )}

              {/* Unlock Icon - Persistent if unlocked and has multiple roles */}
              {player.roles.length > 1 && !player.rolePositionLocked && (
                <div
                  className="absolute -top-1 -left-1 bg-amber-500 text-white rounded-full p-0.5 border-2 border-slate-200 dark:border-slate-800 z-10"
                  title="Role Position Unlocked"
                >
                  <Unlock className="w-3 h-3" />
                </div>
              )}

              {/* Lock Animation Icon - Transient */}
              {showLock && (
                <div
                  className={`absolute -top-1 -left-1 bg-indigo-600 text-white rounded-full p-0.5 border-2 border-slate-200 dark:border-slate-800 z-20 transition-opacity duration-1000 ${fading ? 'opacity-0' : 'opacity-100'}`}
                  title="Role Position Locked"
                >
                  <Lock className="w-3 h-3" />
                </div>
              )}
            </div>
            <div>
              <div>
                <h3 className="font-bold text-slate-900 dark:text-slate-100 text-sm md:text-base leading-tight">
                  <DiscordName userId={player.userId} fallbackName={player.nickname} />
                </h3>
              </div>
              <div className={`flex items-center gap-1.5 mt-1`}>
                <div
                  className={`flex items-center gap-1.5 transition-all ${animate ? 'animate-flash' : ''}`}
                >
                  {!player.userId && (
                    <span className="text-[10px] uppercase tracking-wider font-bold px-1.5 py-0.5 rounded border bg-slate-200 dark:bg-slate-700 border-slate-400 dark:border-slate-600 text-slate-600 dark:text-slate-400">
                      {t('messages.unassigned')}
                    </span>
                  )}
                  {player.roles &&
                    player.roles.length > 0 &&
                    player.roles.map((role, index) => {
                      // Check if this specific role instance is dead
                      const roleName = role;
                      const previousOccurrences = player.roles
                        .slice(0, index)
                        .filter((r) => r === roleName).length;
                      const deadOccurrences = player.deadRoles
                        ? player.deadRoles.filter((r) => r === roleName).length
                        : 0;
                      const isDeadRole = previousOccurrences < deadOccurrences;

                      return (
                        <span
                          key={`${index}-${role}`}
                          onClick={() =>
                            !readonly && isDeadRole
                              ? onAction(player.id, `revive_role:${role}`)
                              : undefined
                          }
                          className={`text-[10px] uppercase tracking-wider font-bold px-1.5 py-0.5 rounded border ${swapAnim[index] || ''}
                            ${isDeadRole ? 'line-through opacity-60 decoration-2 decoration-slate-500' : ''} 
                            ${!readonly && isDeadRole ? 'cursor-pointer hover:opacity-100 hover:decoration-red-500 hover:text-red-600 transition-all' : ''}
                            ${
                              role.includes('狼')
                                ? 'bg-red-100 dark:bg-red-900/30 border-red-300 dark:border-red-800 text-red-700 dark:text-red-300'
                                : role.includes('平民')
                                  ? 'bg-emerald-100 dark:bg-emerald-900/30 border-emerald-300 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300'
                                  : 'bg-indigo-100 dark:bg-indigo-900/30 border-indigo-300 dark:border-indigo-800 text-indigo-700 dark:text-indigo-300'
                            }`}
                          title={
                            !readonly && isDeadRole ? t('players.reviveRole', { role }) : undefined
                          }
                        >
                          {player.roles.length > 1 && `${index + 1}. `}
                          {role}
                        </span>
                      );
                    })}
                </div>
                {!readonly && player.roles.length > 1 && !player.rolePositionLocked && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onAction(player.id, 'switch_role_order');
                    }}
                    className="p-0.5 rounded hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
                    title={t('players.switchOrder')}
                  >
                    <ArrowLeftRight className="w-3 h-3" />
                  </button>
                )}
                {!player.alive && (
                  <span className="text-[10px] text-red-600 dark:text-red-500 font-bold uppercase">
                    {t('players.dead')}
                  </span>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Actions (Admin) */}
      {!readonly && (
        <div className="w-full p-2 grid grid-cols-2 gap-2 bg-slate-50 dark:bg-slate-800 border-t border-slate-200 dark:border-slate-700">
          {player.alive ? (
            <button
              onClick={() => onAction(player.id, 'kill')}
              className="text-xs bg-red-100 dark:bg-red-900/40 hover:bg-red-200 dark:hover:bg-red-900/60 text-red-700 dark:text-red-200 border-red-300 dark:border-red-900/50 py-1.5 rounded border flex items-center justify-center gap-1 transition-colors"
            >
              <Skull className="w-3 h-3" />
              {t('players.kill')}
            </button>
          ) : (
            <button
              onClick={() => onAction(player.id, 'revive')}
              className="text-xs bg-emerald-100 dark:bg-emerald-900/40 hover:bg-emerald-200 dark:hover:bg-emerald-900/60 text-emerald-700 dark:text-emerald-200 py-1.5 rounded border border-emerald-300 dark:border-emerald-900/50 flex items-center justify-center gap-1"
            >
              <HeartPulse className="w-3 h-3" /> {t('players.revive')}
            </button>
          )}
          <button
            onClick={() => onAction(player.id, 'role')}
            className="text-xs bg-slate-200 dark:bg-slate-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-slate-200 py-1.5 rounded border border-slate-400 dark:border-slate-600 flex items-center justify-center gap-1"
          >
            <Settings className="w-3 h-3" /> {t('players.edit')}
          </button>
        </div>
      )}
    </div>
  );
};
