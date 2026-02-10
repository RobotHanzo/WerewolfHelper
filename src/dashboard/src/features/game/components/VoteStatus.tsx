import React, {useMemo} from 'react';
import {Gavel, Skull, Users} from 'lucide-react';
import {Player} from '@/api/types.gen';
import {useTranslation} from '@/lib/i18n';
import {DiscordAvatar, DiscordName} from '@/components/DiscordUser';
import {Timer} from '@/components/Timer';

interface VoteStatusProps {
    candidates: { id: number, voters: string[] }[];
    totalVoters?: number;
    endTime?: number;
    players: Player[];
    title?: string;
    onTimerExpired?: () => void;
    guildId?: string;
}

export const VoteStatus: React.FC<VoteStatusProps> = ({
                                                          candidates,
                                                          totalVoters,
                                                          endTime,
                                                          players,
                                                          guildId
                                                      }) => {
    const {t} = useTranslation();

    // Calculate total votes cast
    const totalVotes = useMemo(() => candidates.reduce((acc, c) => acc + c.voters.length, 0), [candidates]);

    // Identify leading candidates (Suspects on Trial)
    const sortedCandidates = useMemo(() => {
        return [...candidates]
            .filter(c => c.voters.length > 0)
            .sort((a, b) => b.voters.length - a.voters.length);
    }, [candidates]);

    const maxVoteCount = sortedCandidates.length > 0 ? sortedCandidates[0].voters.length : 0;

    // Helper to find who a player voted for
    const getVotedCandidate = (playerUserId: string) => {
        const candidate = candidates.find(c => c.voters.includes(playerUserId));
        if (!candidate) return null;
        return players.find(p => p.id === candidate.id);
    };

    return (
        <div className="flex flex-col gap-12 w-full animate-in fade-in duration-500 overflow-x-hidden">
            {/* Header / Summary Info (Internal) */}
            <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
                <div>
                    <h2 className="text-3xl font-black text-slate-900 dark:text-white tracking-tight flex items-center gap-3">
                        <Gavel className="w-8 h-8 text-[#3211d4] dark:text-indigo-400"/>
                        {t('steps.votingPhase', 'Voting Phase')}
                    </h2>
                    <p className="text-slate-500 dark:text-slate-400 text-sm mt-1">
                        {t('vote.statusDescription', 'Live tally of village consensus and player participation')}
                    </p>
                </div>
                <Timer
                    endTime={Number(endTime || 0)}
                    label={t('vote.timeLeft', 'Time Remaining')}
                />
            </div>

            {/* Suspects on Trial Section */}
            <div className="space-y-6">
                <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-6">
                    <div className="min-w-0">
                        <h3 className="text-xl font-black text-slate-900 dark:text-white tracking-tight uppercase">
                            {t('vote.suspectsOnTrial', 'Suspects on Trial')}
                        </h3>
                        <p className="text-slate-500 dark:text-slate-400 text-xs font-semibold uppercase tracking-wider mt-0.5 truncate">
                            {t('vote.leadingCandidates', 'Main candidates identified by the village')}
                        </p>
                    </div>
                    <div className="flex flex-col items-end gap-2 w-full sm:w-auto sm:min-w-[200px]">
                        <div
                            className="flex justify-between w-full text-[10px] font-black uppercase tracking-widest text-slate-500 dark:text-slate-400 gap-4">
                            <span className="truncate">{t('vote.participation', 'Participation')}</span>
                            <span className="text-[#3211d4] dark:text-indigo-400 whitespace-nowrap">
                                {totalVotes} / {totalVoters || 0}
                            </span>
                        </div>
                        <div
                            className="h-2 w-full bg-slate-100 dark:bg-white/10 rounded-full overflow-hidden p-[1px] border border-slate-200 dark:border-white/5">
                            <div
                                className="h-full bg-[#3211d4] dark:bg-indigo-500 rounded-full transition-all duration-700 ease-out shadow-[0_0_8px_rgba(50,17,212,0.2)] dark:shadow-[0_0_8px_rgba(129,140,248,0.2)]"
                                style={{width: `${totalVoters ? Math.min(100, (totalVotes / totalVoters) * 100) : 0}%`}}
                            />
                        </div>
                    </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                    {sortedCandidates.length === 0 ? (
                        <div
                            className="col-span-full py-16 bg-slate-50 dark:bg-white/5 border border-dashed border-slate-300 dark:border-white/10 rounded-2xl flex flex-col items-center justify-center text-slate-400 text-sm italic">
                            <div
                                className="w-16 h-16 rounded-full bg-slate-100 dark:bg-white/5 flex items-center justify-center mb-4">
                                <Users className="w-8 h-8 opacity-20"/>
                            </div>
                            {t('vote.waitingInitialVotes', 'Waiting for initial votes to determine suspects...')}
                        </div>
                    ) : (
                        sortedCandidates.slice(0, 4).map((candidate, idx) => {
                            const player = players.find(p => p.id === candidate.id);
                            const isLeading = candidate.voters.length === maxVoteCount && candidate.voters.length > 0;
                            const voteRatio = totalVotes > 0 ? (candidate.voters.length / totalVotes) * 100 : 0;

                            return (
                                <div key={candidate.id}
                                     className="relative group animate-in fade-in slide-in-from-bottom-4 duration-500 fill-mode-both"
                                     style={{animationDelay: `${idx * 150}ms`}}>
                                    {isLeading && (
                                        <div
                                            className="absolute -inset-0.5 bg-[#3211d4]/20 rounded-2xl blur opacity-30 group-hover:opacity-60 transition duration-1000 candidate-glow"></div>
                                    )}
                                    <div
                                        className={`relative bg-white dark:bg-slate-900 border ${isLeading ? 'border-[#3211d4]/50' : 'border-slate-200 dark:border-white/10'} rounded-2xl p-6 flex flex-col gap-6 backdrop-blur-sm shadow-xl hover:translate-y-[-4px] transition-all duration-300 min-w-0`}>
                                        <div className="flex justify-between items-start gap-4">
                                            <div className="flex gap-5 min-w-0">
                                                <div
                                                    className={`size-20 rounded-2xl overflow-hidden shadow-lg ${isLeading ? 'ring-2 ring-[#3211d4]/50' : 'ring-2 ring-white/10'} bg-slate-800 flex-shrink-0`}>
                                                    <DiscordAvatar userId={String(player?.userId || '')}
                                                                   guildId={guildId}
                                                                   avatarClassName="size-full object-cover"/>
                                                </div>
                                                <div className="min-w-0">
                                                    <div className="flex items-center gap-3 flex-nowrap min-w-0">
                                                        <h4 className="text-xl font-black text-slate-900 dark:text-white leading-none truncate">
                                                            <DiscordName userId={String(player?.userId || '')}
                                                                         guildId={guildId}
                                                                         fallbackName={player?.nickname || `Player ${candidate.id}`}/>
                                                        </h4>
                                                        {isLeading && (
                                                            <span
                                                                className="px-2 py-0.5 bg-[#3211d4] text-[10px] font-black uppercase rounded tracking-tighter text-white flex-shrink-0">
                                                                {t('vote.leading', 'Leading')}
                                                            </span>
                                                        )}
                                                    </div>
                                                    <p className="text-slate-500 dark:text-slate-400 text-xs font-bold uppercase mt-2 tracking-widest truncate">
                                                        {player?.roles?.[0] || t('roles.unknown')}
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="text-right flex-shrink-0">
                                                <div
                                                    className="text-5xl font-black text-[#3211d4] dark:text-indigo-400 leading-none drop-shadow-sm">
                                                    {candidate.voters.length}
                                                </div>
                                                <div
                                                    className="text-[10px] font-bold text-slate-500 uppercase mt-2 tracking-widest">
                                                    {t('vote.received', 'Votes Received')}
                                                </div>
                                            </div>
                                        </div>
                                        <div className="space-y-2.5">
                                            <div
                                                className="flex justify-between text-[11px] font-black text-slate-500 dark:text-slate-400 uppercase tracking-widest">
                                                <span>{t('vote.consensus', 'Consensus')}</span>
                                                <span
                                                    className={isLeading ? 'text-[#3211d4] dark:text-indigo-400' : ''}>{Math.round(voteRatio)}%</span>
                                            </div>
                                            <div
                                                className="h-2.5 w-full bg-slate-100 dark:bg-white/10 rounded-full overflow-hidden p-[1px]">
                                                <div
                                                    className={`h-full rounded-full transition-all duration-1000 ease-out ${isLeading ? 'bg-[#3211d4] dark:bg-indigo-500 shadow-[0_0_15px_rgba(50,17,212,0.8)] dark:shadow-[0_0_15px_rgba(129,140,248,0.5)]' : 'bg-slate-400 dark:bg-slate-600'}`}
                                                    style={{width: `${voteRatio}%`}}
                                                ></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            );
                        })
                    )}
                </div>
            </div>

            {/* Voting Map Section */}
            <div className="space-y-8 pt-4">
                <div
                    className="flex flex-col lg:flex-row lg:items-end justify-between border-b border-slate-200 dark:border-white/10 pb-4 gap-4">
                    <div className="min-w-0">
                        <h3 className="text-lg font-black text-slate-900 dark:text-white tracking-tight uppercase">
                            {t('vote.votingMap', 'Voting Map')}
                        </h3>
                    </div>
                    <div
                        className="flex flex-wrap gap-x-6 gap-y-2 text-[10px] font-black uppercase tracking-wider text-slate-500">
                        <div className="flex items-center gap-2 mb-0.5">
                            <span
                                className="size-2.5 rounded-full bg-[#3211d4] dark:bg-indigo-400"></span> {t('vote.voted', 'Voted')}
                        </div>
                        <div className="flex items-center gap-2 mb-0.5">
                            <span
                                className="size-2.5 rounded-full bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.5)]"></span> {t('vote.thinking', 'Thinking')}
                        </div>
                        <div className="flex items-center gap-2 mb-0.5 opacity-50">
                            <span
                                className="size-2.5 rounded-full bg-slate-400 dark:bg-slate-800"></span> {t('vote.eliminated', 'Eliminated')}
                        </div>
                    </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 xl:grid-cols-8 gap-4">
                    {players.map((player) => {
                        const isDead = !player.alive;
                        const votedFor = getVotedCandidate(String(player.userId || ''));
                        const isThinking = !isDead && !votedFor;

                        return (
                            <div key={player.id} className={`bg-white dark:bg-slate-900/50 border transition-all duration-300 rounded-xl p-3 flex flex-col items-center gap-2 relative group shadow-sm overflow-hidden min-w-0
                                ${isDead ? 'opacity-40 grayscale border-slate-100 dark:border-white/5' :
                                votedFor ? 'border-[#3211d4]/30 hover:bg-[#3211d4]/5 select-none' :
                                    'border-slate-200 dark:border-white/10 hover:border-amber-500/50 hover:bg-amber-500/5'}`}>

                                <div className={`size-14 rounded-xl overflow-hidden transition-all duration-500 
                                    ${votedFor ? 'ring-2 ring-[#3211d4]/30' :
                                    isThinking ? 'ring-2 ring-amber-500/20 animate-pulse' :
                                        'bg-slate-900'}`}>
                                    {isDead ? (
                                        <div
                                            className="size-full bg-slate-200 dark:bg-slate-950 flex items-center justify-center">
                                            <Skull className="w-8 h-8 text-red-500 opacity-60"/>
                                        </div>
                                    ) : (
                                        <DiscordAvatar userId={String(player.userId || '')} guildId={guildId}
                                                       avatarClassName="size-full object-cover group-hover:scale-110 transition-transform duration-500"/>
                                    )}
                                </div>
                                <span
                                    className={`text-[10px] font-black uppercase tracking-widest truncate max-w-full ${isDead ? 'text-slate-500 line-through' : 'text-slate-900 dark:text-white'}`}>
                                    <DiscordName userId={String(player.userId || '')} guildId={guildId}
                                                 fallbackName={player.nickname}/>
                                </span>
                                {isDead ? (
                                    <span
                                        className="text-[9px] px-2 py-0.5 rounded-full bg-red-950/20 text-red-500 border border-red-900/10 font-black uppercase tracking-tighter">
                                        {t('dashboard.eliminated', 'DEAD')}
                                    </span>
                                ) : votedFor ? (
                                    <span
                                        className="text-[9px] px-2 py-0.5 rounded-full bg-[#3211d4]/10 dark:bg-indigo-400/10 text-[#3211d4] dark:text-indigo-400 border border-[#3211d4]/20 dark:border-indigo-400/20 font-black uppercase tracking-tighter truncate max-w-full">
                                        <DiscordName userId={String(votedFor.userId || '')} guildId={guildId}
                                                     fallbackName={votedFor.nickname}/>
                                    </span>
                                ) : (
                                    <span
                                        className="text-[9px] px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-500 border border-amber-500/20 font-black uppercase tracking-tighter animate-pulse">
                                        {t('vote.thinkingMap', 'Thinking...')}
                                    </span>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
};
