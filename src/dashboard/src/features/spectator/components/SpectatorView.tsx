import React, { useMemo } from 'react';
import { useTranslation } from '@/lib/i18n';
import { Player } from '@/api/types.gen';
import { Activity, Info, Shield, Skull, Users, Zap } from 'lucide-react';
import { DiscordAvatar, DiscordName } from '@/components/DiscordUser';

interface SpectatorViewProps {
  players: Player[];
  doubleIdentities: boolean;
}

export const SpectatorView: React.FC<SpectatorViewProps> = ({ players, doubleIdentities }) => {
  const { t } = useTranslation();

  const stats = useMemo(() => {
    let wolves = 0;
    let deadWolves = 0;
    let gods = 0;
    let deadGods = 0;
    let villagers = 0;
    let deadVillagers = 0;
    let jbbs = 0;
    let deadJbbs = 0;

    players.forEach((player) => {
      const pRoles = player.roles || [];
      const isDead = !player.alive;

      if (player.jinBaoBao) {
        jbbs++;
        if (isDead) deadJbbs++;
      }

      pRoles.forEach((role) => {
        const uRole = role.toUpperCase();
        const isWolf =
          role.includes('狼') ||
          [
            'WEREWOLF',
            'GARGOYLE',
            'BLOOD_MOON',
            'NIGHTMARE',
            'WOLF_KING',
            'WHITE_WOLF_KING',
            'WOLF_BROTHER',
            'HIDDEN_WOLF',
          ].includes(uRole);
        const isVillager = role === '平民' || uRole === 'VILLAGER';
        const isGod = !isWolf && !isVillager;

        if (isWolf) {
          wolves++;
          if (isDead) deadWolves++;
        } else if (isVillager) {
          villagers++;
          if (isDead) deadVillagers++;
        } else if (isGod) {
          gods++;
          if (isDead) deadGods++;
        }
      });
    });

    return {
      wolves,
      deadWolves,
      gods,
      deadGods,
      villagers,
      deadVillagers,
      jbbs,
      deadJbbs,
    };
  }, [players]);

  const winProgressWolves =
    stats.gods + stats.villagers > 0
      ? ((stats.deadGods + stats.deadVillagers) / (stats.gods + stats.villagers)) * 100
      : 0;
  const winProgressGood = stats.wolves > 0 ? (stats.deadWolves / stats.wolves) * 100 : 0;
  const winProgressJBB = stats.wolves > 0 ? (stats.deadWolves / stats.wolves) * 100 : 0;

  return (
    <div className="text-white font-['Spline_Sans']">
      {/* Header */}
      <header className="flex items-center justify-between mb-12">
        <div className="space-y-1.5">
          <div className="flex items-baseline gap-4">
            <h1 className="text-4xl font-black tracking-tight">{t('spectator.title')}</h1>
          </div>
        </div>
      </header>

      <div className="grid grid-cols-12 gap-12">
        {/* Left Sidebar: Faction Statistics */}
        <aside className="col-span-3 space-y-6">
          <div className="flex items-center gap-2 text-slate-400 font-black text-sm uppercase tracking-[0.3em] mb-4">
            <Activity className="w-4 h-4" />
            <span>{t('spectator.factionStats')}</span>
          </div>

          <div className="space-y-3">
            <FactionStatCard
              name={t('roles.factions.werewolf')}
              icon={<Skull className="w-5 h-5 text-red-500" />}
              current={stats.wolves - stats.deadWolves}
              total={stats.wolves}
              color="red"
            />
            <FactionStatCard
              name={t('roles.factions.special')}
              icon={<Zap className="w-5 h-5 text-purple-500" />}
              current={stats.gods - stats.deadGods}
              total={stats.gods}
              color="purple"
            />
            {doubleIdentities ? (
              <FactionStatCard
                name={t('status.jinBaoBao')}
                icon={<Activity className="w-5 h-5 text-pink-500" />}
                current={stats.jbbs - stats.deadJbbs}
                total={stats.jbbs}
                color="pink"
              />
            ) : (
              <FactionStatCard
                name={t('roles.factions.civilian')}
                icon={<Shield className="w-5 h-5 text-blue-500" />}
                current={stats.villagers - stats.deadVillagers}
                total={stats.villagers}
                color="blue"
              />
            )}
          </div>

          <div className="pt-4">
            <div className="p-4 rounded-2xl bg-indigo-500/5 border border-indigo-500/10 space-y-3">
              <div className="flex items-center gap-2 text-indigo-400">
                <Info className="w-4 h-4" />
                <span className="font-black text-sm uppercase tracking-wider">
                  {t('spectator.winConditions')}
                </span>
              </div>
              <div className="space-y-2">
                <p className="text-base text-slate-400 leading-relaxed font-bold">
                  • {t('spectator.goodWinCondition')}
                </p>
                <p className="text-base text-slate-400 leading-relaxed font-bold">
                  •{' '}
                  {doubleIdentities
                    ? t('spectator.wolfWinConditionDouble')
                    : t('spectator.wolfWinConditionNormal')}
                </p>
              </div>
            </div>
          </div>
        </aside>

        {/* Main Content */}
        <main className="col-span-9 space-y-12">
          {/* Win Tracking Banner */}
          <section className="bg-slate-900/30 border border-slate-800/50 rounded-3xl p-10 relative overflow-hidden backdrop-blur-md">
            <div className="flex items-center justify-between gap-12">
              {/* Good Faction Progress */}
              <div className="flex-1 space-y-5">
                <div className="flex items-center justify-between text-xl font-black tracking-widest text-blue-400 uppercase">
                  <span>
                    {doubleIdentities && stats.jbbs > 0
                      ? t('status.jinBaoBao')
                      : t('roles.factions.civilian')}{' '}
                    & {t('roles.factions.special')}
                  </span>
                  <span>{Math.round(doubleIdentities ? winProgressJBB : winProgressGood)}%</span>
                </div>
                <div className="h-5 w-full bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className={`h-full bg-gradient-to-r ${doubleIdentities ? 'from-pink-600 to-purple-500' : 'from-blue-600 to-purple-500'} transition-all duration-1000`}
                    style={{ width: `${doubleIdentities ? winProgressJBB : winProgressGood}%` }}
                  />
                </div>
              </div>

              {/* VS Text */}
              <div className="flex items-center justify-center shrink-0 px-4">
                <span className="font-black text-4xl tracking-tighter text-slate-500">VS</span>
              </div>

              {/* Wolf Faction Progress */}
              <div className="flex-1 space-y-5">
                <div className="flex items-center justify-between text-xl font-black tracking-widest text-red-400 uppercase">
                  <span>{Math.round(winProgressWolves)}%</span>
                  <span>{t('roles.factions.werewolf')}</span>
                </div>
                <div className="h-5 w-full bg-slate-800 rounded-full overflow-hidden flex justify-end">
                  <div
                    className="h-full bg-gradient-to-l from-red-600 to-red-900 transition-all duration-1000"
                    style={{ width: `${winProgressWolves}%` }}
                  />
                </div>
              </div>
            </div>
          </section>

          {/* Player Grid */}
          <section className="space-y-6">
            <div className="flex items-center gap-4">
              <div className="flex items-center gap-2 text-slate-400 font-bold text-lg uppercase tracking-[0.2em]">
                <Users className="w-5 h-5" />
                <span>{t('players.title')}</span>
              </div>
              <div className="flex items-center gap-2 px-3 py-1 bg-emerald-500/10 border border-emerald-500/20 rounded-full">
                <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]" />
                <span className="text-xs font-black text-emerald-500 uppercase tracking-widest">
                  {players.filter((p) => p.alive).length} {t('players.alive')}
                </span>
              </div>
            </div>

            <div className="grid grid-cols-6 gap-6">
              {players.map((player, idx) => (
                <SpectatorPlayerCard key={player.id || idx} player={player} />
              ))}
            </div>
          </section>
        </main>
      </div>
    </div>
  );
};

interface FactionStatCardProps {
  name: string;
  icon: React.ReactNode;
  current: number;
  total: number;
  color: 'red' | 'purple' | 'blue' | 'amber' | 'pink';
}

const FactionStatCard: React.FC<FactionStatCardProps> = ({ name, icon, current, total, color }) => {
  const progress = total > 0 ? (current / total) * 100 : 0;

  const colors = {
    red: 'from-red-500/20 text-red-500 border-red-500/20 bg-red-500',
    purple: 'from-purple-500/20 text-purple-500 border-purple-500/20 bg-purple-500',
    blue: 'from-blue-500/20 text-blue-500 border-blue-500/20 bg-blue-500',
    amber: 'from-amber-500/20 text-amber-500 border-amber-500/20 bg-amber-500',
    pink: 'from-pink-500/20 text-pink-500 border-pink-500/20 bg-pink-500',
  };

  return (
    <div
      className={`p-3 px-4 rounded-xl bg-gradient-to-br transition-all duration-300 border ${colors[color].split(' ')[2]} bg-slate-900/40 relative overflow-hidden group hover:bg-slate-800/60`}
    >
      <div className="flex items-center justify-between mb-2 relative z-10">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-slate-950/50 border border-white/5 transition-transform group-hover:scale-110 duration-500">
            {icon}
          </div>
          <span className="font-bold text-lg tracking-tight text-slate-100">{name}</span>
        </div>
        <div className="flex items-baseline gap-1">
          <span className="text-2xl font-black text-white">{current}</span>
          <span className="text-sm font-bold text-slate-500">/ {total}</span>
        </div>
      </div>

      <div className="h-1.5 w-full bg-slate-800/50 rounded-full overflow-hidden relative z-10">
        <div
          className={`h-full opacity-60 transition-all duration-1000 ${colors[color].split(' ')[3]}`}
          style={{ width: `${progress}%` }}
        />
      </div>
      {/* Background Accent */}
      <div
        className={`absolute -right-4 -bottom-4 w-16 h-16 rounded-full blur-2xl opacity-10 transition-opacity group-hover:opacity-20 ${colors[color].split(' ')[4]}`}
      />
    </div>
  );
};

const SpectatorPlayerCard: React.FC<{ player: Player }> = ({ player }) => {
  const { t } = useTranslation();
  const isDead = !player.alive;
  const role = player.roles?.[0] || t('roles.unknown');
  const uRole = (player.roles?.[0] || '').toUpperCase();

  // Faction determination for border color
  let factionColor = 'border-slate-800';
  let factionGlow = 'from-slate-500/20';

  const isWolf =
    role.includes('狼') ||
    [
      'WEREWOLF',
      'GARGOYLE',
      'BLOOD_MOON',
      'NIGHTMARE',
      'WOLF_KING',
      'WHITE_WOLF_KING',
      'WOLF_BROTHER',
      'HIDDEN_WOLF',
    ].includes(uRole);
  const isGod =
    [
      'WITCH',
      'SEER',
      'HUNTER',
      'GUARD',
      'IDIOT',
      'KNIGHT',
      'GRAVE_KEEPER',
      'DREAM_EATER',
      'MAGICIAN',
    ].includes(uRole) ||
    (role !== '平民' && uRole !== 'VILLAGER' && !isWolf);

  if (isWolf) {
    factionColor = 'border-red-500/50';
    factionGlow = 'from-red-500/10';
  } else if (isGod) {
    factionColor = 'border-purple-500/50';
    factionGlow = 'from-purple-500/10';
  } else {
    factionColor = 'border-blue-500/50';
    factionGlow = 'from-blue-500/10';
  }

  return (
    <div
      className={`relative group p-4 rounded-3xl bg-slate-900/40 border transition-all duration-500 ${isDead ? 'border-slate-800 grayscale saturate-0 opacity-60' : `${factionColor} hover:border-white/20`} hover:bg-slate-800/50 hover:-translate-y-1`}
    >
      {/* Background Glow */}
      {!isDead && (
        <div
          className={`absolute inset-0 bg-gradient-to-b ${factionGlow} to-transparent opacity-0 group-hover:opacity-100 transition-opacity rounded-3xl`}
        />
      )}

      <div className="relative z-10 flex flex-col items-center gap-3">
        {/* Avatar Section */}
        <div className="relative">
          <div
            className={`w-16 h-16 rounded-full border-2 ${isDead ? 'border-slate-700' : factionColor.replace('50', '80')} p-1 transition-transform group-hover:scale-105 duration-500 ring-4 ring-white/5`}
          >
            <div className="w-full h-full rounded-full bg-slate-800 overflow-hidden relative">
              {/* User Image */}
              {player.userId ? (
                <DiscordAvatar
                  userId={player.userId}
                  avatarClassName="w-full h-full object-cover"
                />
              ) : (
                <div className="w-full h-full bg-gradient-to-br from-slate-700 to-slate-900 flex items-center justify-center">
                  <Users className="w-6 h-6 text-slate-500" />
                </div>
              )}
              {isDead && (
                <div className="absolute inset-0 bg-slate-950/80 flex items-center justify-center">
                  <Skull className="w-6 h-6 text-slate-600" />
                </div>
              )}
            </div>
          </div>
          {/* Status Dot */}
          {!isDead && (
            <div className="absolute top-0 right-0 w-3 h-3 bg-emerald-500 border-2 border-[#1A1C23] rounded-full shadow-lg" />
          )}
        </div>

        {/* Info Section */}
        <div className="text-center space-y-1">
          <h3 className="text-base font-black text-white tracking-tight truncate max-w-[100px]">
            <DiscordName userId={player.userId} fallbackName={player.nickname} />
          </h3>
          <div
            className={`inline-block px-2 py-0.5 rounded-md text-[9px] font-black uppercase tracking-wider ${isDead ? 'bg-slate-800 text-slate-500' : 'bg-white/5 text-slate-300'}`}
          >
            {role}
          </div>
        </div>
      </div>
    </div>
  );
};
