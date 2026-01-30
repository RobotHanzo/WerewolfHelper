import { BadgeAlert, HeartPulse, Shield, Skull, MicOff, Settings } from 'lucide-react';
import { Player } from '../types';
import { useTranslation } from '../lib/i18n';

interface PlayerCardProps {
  player: Player;
  onAction: (id: string, action: string) => void;
}

export const PlayerCard: React.FC<PlayerCardProps> = ({ player, onAction }) => {
  const { t } = useTranslation();
  const cardStyle = "bg-slate-100 dark:bg-slate-800/50 rounded-xl border border-slate-300 dark:border-slate-700/50 hover:border-indigo-400 dark:hover:border-indigo-500/50 transition-all duration-200 overflow-hidden relative group";

  return (
    <div className={`${cardStyle} ${!player.isAlive ? 'opacity-60 grayscale' : ''}`}>
      <div className="p-4 flex flex-col gap-4">
        {/* Header */}
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="relative">
              <img
                src={player.avatar}
                alt={player.name}
                className="w-12 h-12 rounded-full border-2 border-slate-400 dark:border-slate-600 bg-slate-300 dark:bg-slate-700 object-cover"
              />
              {player.isSheriff && (
                <div className="absolute -bottom-1 -right-1 bg-yellow-500 text-black rounded-full p-0.5 border-2 border-slate-200 dark:border-slate-800" title={t('status.sheriff')}>
                  <BadgeAlert className="w-3 h-3" />
                </div>
              )}
              {player.isJinBaoBao && (
                <div className="absolute -top-1 -right-1 bg-pink-500 text-white rounded-full p-0.5 border-2 border-slate-200 dark:border-slate-800" title={t('status.jinBaoBao')}>
                  <HeartPulse className="w-3 h-3" />
                </div>
              )}
            </div>
            <div>
              <h3 className="font-bold text-slate-900 dark:text-slate-100 text-sm md:text-base">{player.name}</h3>
              <div className="flex items-center gap-1.5 mt-0.5">
                <span className={`text-[10px] uppercase tracking-wider font-bold px-1.5 py-0.5 rounded border ${player.role === 'WEREWOLF' ? 'bg-red-100 dark:bg-red-900/30 border-red-300 dark:border-red-800 text-red-700 dark:text-red-300' :
                    player.role === 'VILLAGER' ? 'bg-emerald-100 dark:bg-emerald-900/30 border-emerald-300 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300' :
                      'bg-indigo-100 dark:bg-indigo-900/30 border-indigo-300 dark:border-indigo-800 text-indigo-700 dark:text-indigo-300'
                  }`}>
                  {t(`roles.${player.role}`)}
                </span>
                {!player.isAlive && <span className="text-[10px] text-red-600 dark:text-red-500 font-bold uppercase">{t('players.dead')}</span>}
              </div>
            </div>
          </div>
          <div className="flex flex-col gap-1">
            {player.isProtected && <div title={t('status.protected')}><Shield className="w-4 h-4 text-blue-500 dark:text-blue-400" /></div>}
            {player.isPoisoned && <div title={t('status.poisoned')}><Skull className="w-4 h-4 text-green-500 dark:text-green-400" /></div>}
            {player.isSilenced && <div title={t('status.silenced')}><MicOff className="w-4 h-4 text-slate-500 dark:text-slate-400" /></div>}
          </div>
        </div>

        {/* Actions (Admin) */}
        <div className="grid grid-cols-2 gap-2 mt-2 opacity-0 group-hover:opacity-100 transition-opacity">
          {player.isAlive ? (
            <button
              onClick={() => onAction(player.id, 'kill')}
              className="text-xs bg-red-100 dark:bg-red-900/40 hover:bg-red-200 dark:hover:bg-red-900/60 text-red-700 dark:text-red-200 py-1.5 rounded border border-red-300 dark:border-red-900/50 flex items-center justify-center gap-1"
            >
              <Skull className="w-3 h-3" /> {t('players.kill')}
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
      </div>
    </div>
  );
};
