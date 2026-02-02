import React, {useMemo} from 'react';
import {useTranslation} from '@/lib/i18n';
import {Player} from '@/types';
import {HeartPulse, Shield, Skull, Users, Zap} from 'lucide-react';
import {PlayerCard} from '@/features/players/components/PlayerCard';

interface SpectatorViewProps {
    players: Player[];
    doubleIdentities: boolean;
}

export const SpectatorView: React.FC<SpectatorViewProps> = ({players, doubleIdentities}) => {
    const {t} = useTranslation();

    const stats = useMemo(() => {
        let wolves = 0;
        let deadWolves = 0;
        let gods = 0;
        let deadGods = 0;
        let villagers = 0;
        let deadVillagers = 0;
        let jinBaoBaos = 0;
        let deadJinBaoBaos = 0;

        // Detailed Iteration
        players.forEach(player => {
            // JBB Logic
            if (player.isJinBaoBao) {
                jinBaoBaos++;
                if (!player.isAlive) {
                    deadJinBaoBaos++;
                }
            }

            // Roles Logic
            const pRoles = player.roles || [];
            const pDeadRoles = player.deadRoles || [];

            // We need to match dead roles to actual roles to calculate stats.
            // We can just count totals.

            let localDead = [...pDeadRoles];

            pRoles.forEach(role => {
                const isWolf = role.includes('狼') || role === '石像鬼' || role === '血月使者' || role === '惡靈騎士' || role === '夢魘';
                const isVillager = role === '平民';
                const isGod = !isWolf && !isVillager;

                // Check if this role is dead
                let isDead = false;
                const deadIdx = localDead.indexOf(role);
                if (deadIdx !== -1) {
                    isDead = true;
                    localDead.splice(deadIdx, 1); // Remove matched dead role
                }

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
            wolves, deadWolves,
            gods, deadGods,
            villagers, deadVillagers,
            jinBaoBaos, deadJinBaoBaos
        };
    }, [players]);

    return (
        <div className="space-y-6">
            <div className="text-center space-y-2">
                <h2 className="text-2xl font-bold text-slate-900 dark:text-slate-100">{t('spectator.title')}</h2>
                <p className="text-slate-500 dark:text-slate-400">{t('spectator.subtitle')}</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {/* Wolves Status */}
                <FactionCard
                    title={t('spectator.wolvesLeft')}
                    icon={<Skull className="w-6 h-6 text-red-500"/>}
                    color="red"
                    current={stats.wolves - stats.deadWolves}
                    total={stats.wolves}
                    description={t('spectator.wolvesLeftDesc')}
                />

                {/* Good Faction Status */}
                {doubleIdentities ? (
                    <>
                        <FactionCard
                            title={t('spectator.godsLeft')}
                            icon={<Zap className="w-6 h-6 text-yellow-500"/>}
                            color="yellow"
                            current={stats.gods - stats.deadGods}
                            total={stats.gods}
                            description={t('spectator.godsLeftDesc')}
                        />
                        <FactionCard
                            title={t('spectator.jbbLeft')}
                            icon={<HeartPulse className="w-6 h-6 text-pink-500"/>}
                            color="pink"
                            current={stats.jinBaoBaos - stats.deadJinBaoBaos}
                            total={stats.jinBaoBaos}
                            description={t('spectator.jbbLeftDesc')}
                        />
                    </>
                ) : (
                    <>
                        <FactionCard
                            title={t('spectator.godsLeft')}
                            icon={<Zap className="w-6 h-6 text-yellow-500"/>}
                            color="yellow"
                            current={stats.gods - stats.deadGods}
                            total={stats.gods}
                            description={t('spectator.godsLeftDesc')}
                        />
                        <FactionCard
                            title={t('spectator.villagersLeft')}
                            icon={<Shield className="w-6 h-6 text-emerald-500"/>}
                            color="emerald"
                            current={stats.villagers - stats.deadVillagers}
                            total={stats.villagers}
                            description={t('spectator.villagersLeftDesc')}
                        />
                    </>
                )}
            </div>

            <div
                className="bg-slate-100 dark:bg-slate-800/50 p-4 rounded-lg text-sm text-slate-600 dark:text-slate-400">
                <h3 className="font-bold mb-2">{t('spectator.winConditions')}</h3>
                <ul className="list-disc list-inside space-y-1">
                    <li>{t('spectator.goodWinCondition')}</li>
                    <li>{doubleIdentities ? t('spectator.wolfWinConditionDouble') : t('spectator.wolfWinConditionNormal')}</li>
                </ul>
            </div>

            {/* Read-only Player Grid */}
            <div className="space-y-4">
                <h3 className="text-xl font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                    <Users className="w-5 h-5 text-slate-500 dark:text-slate-400"/>
                    {t('players.title')} <span
                    className="text-slate-500 dark:text-slate-500 text-sm font-normal">({players.filter(p => p.isAlive).length} {t('players.alive')})</span>
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                    {players.map(player => (
                        <PlayerCard
                            key={player.id}
                            player={player}
                            onAction={() => {
                            }}
                            readonly={true}
                        />
                    ))}
                </div>
            </div>


        </div>
    );
};

interface FactionCardProps {
    title: string;
    icon: React.ReactNode;
    color: 'red' | 'yellow' | 'emerald' | 'pink';
    current: number;
    total: number;
    description: string;
}

const FactionCard: React.FC<FactionCardProps> = ({title, icon, color, current, total, description}) => {
    const {t} = useTranslation();
    const percentage = total > 0 ? ((total - current) / total) * 100 : 0;

    const colorClasses = {
        red: {bg: 'bg-red-500', bar: 'bg-red-500', text: 'text-red-600 dark:text-red-400'},
        yellow: {bg: 'bg-yellow-500', bar: 'bg-yellow-500', text: 'text-yellow-600 dark:text-yellow-400'},
        emerald: {bg: 'bg-emerald-500', bar: 'bg-emerald-500', text: 'text-emerald-600 dark:text-emerald-400'},
        pink: {bg: 'bg-pink-500', bar: 'bg-pink-500', text: 'text-pink-600 dark:text-pink-400'},
    };

    return (
        <div
            className="bg-white dark:bg-slate-900 rounded-xl border border-slate-200 dark:border-slate-800 p-6 shadow-sm">
            <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-3">
                    <div className={`p-2 rounded-lg bg-slate-100 dark:bg-slate-800`}>
                        {icon}
                    </div>
                    <div>
                        <h3 className="font-bold text-slate-900 dark:text-slate-100">{title}</h3>
                        <p className="text-xs text-slate-500">{description}</p>
                    </div>
                </div>
                <div className="text-2xl font-bold font-mono">
                    {current}/{total}
                </div>
            </div>

            <div className="space-y-2">
                <div className="flex justify-between text-xs font-medium text-slate-500">
                    <span>{t('messages.progress')}</span>
                    <span>{Math.round(percentage)}%</span>
                </div>
                <div className="h-3 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden">
                    <div
                        className={`h-full rounded-full transition-all duration-500 ${colorClasses[color].bar}`}
                        style={{width: `${percentage}%`}}
                    />
                </div>
            </div>
        </div>
    );
};
