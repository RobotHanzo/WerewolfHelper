import React, {useEffect, useMemo, useRef, useState} from 'react';
import {
    AlertCircle,
    ArrowRight,
    Ban,
    Brain,
    Check,
    Clock,
    Eye,
    FlaskConical,
    Lightbulb,
    MessageSquare,
    Moon,
    Shield,
    Target,
    User,
    Users
} from 'lucide-react';
import {GameStateDto as GameState, Player, RoleActionInstance, WolfMessage, WolfVote} from '@/api/types.gen';
import {DiscordAvatar, DiscordName} from '@/components/DiscordUser';
import {useTranslation} from '@/lib/i18n';

// --- Types ---
interface EnrichedActionStatus extends RoleActionInstance {
    playerName: string;
    playerUserId?: string | number | bigint;
    targetName: string | null;
    targetUserId?: string | number | bigint;
    targetRole?: string;
    actionName: string | null;
}

interface NightStatusData {
    day: number;
    phaseType: 'WEREWOLF_VOTING' | 'ROLE_ACTIONS' | 'WOLF_YOUNGER_BROTHER_ACTION';
    startTime: number;
    endTime: number;
    werewolfMessages: WolfMessage[];
    werewolfVotes: WolfVote[];
    actionStatuses: RoleActionInstance[];
}

// --- Main Component ---

interface NightStatusProps {
    guildId?: string;
    players?: Player[];
    gameState?: GameState;
}

export const NightStatus: React.FC<NightStatusProps> = ({guildId, players = [], gameState}) => {
    const {t} = useTranslation();
    const [activeTab, setActiveTab] = useState<'werewolves' | 'actions'>('werewolves');
    const messageScrollContainerRef = useRef<HTMLDivElement>(null);

    // Data Processing
    const nightStatus = useMemo<NightStatusData | null>(() => {
        if (!gameState?.stateData) return null;

        const stateData = gameState.stateData as any;
        const phaseType = stateData.phaseType || 'WEREWOLF_VOTING';

        // Werewolf Messages
        const werewolfMessages = (stateData.werewolfMessages || []) as WolfMessage[];

        // Werewolf Votes
        const wolfState = stateData.wolfStates?.['WEREWOLF_KILL'];
        const werewolfVotes = (wolfState?.votes || []) as WolfVote[];

        // Action Statuses
        const actionStatuses = (stateData.submittedActions || []) as RoleActionInstance[];

        return {
            day: gameState.day || 0,
            phaseType,
            startTime: stateData.phaseStartTime || Date.now(),
            endTime: stateData.phaseEndTime || (Date.now() + 60000),
            werewolfMessages,
            werewolfVotes,
            actionStatuses
        };
    }, [gameState, players]);

    // Helpers
    const getRemainingSeconds = (): number => {
        if (!nightStatus) return 0;
        return Math.max(0, Math.floor((nightStatus.endTime - Date.now()) / 1000));
    };

    // Effects
    useEffect(() => {
        if (activeTab === 'werewolves' && messageScrollContainerRef.current) {
            messageScrollContainerRef.current.scrollTop = messageScrollContainerRef.current.scrollHeight;
        }
    }, [nightStatus?.werewolfMessages, activeTab]);

    useEffect(() => {
        const type = nightStatus?.phaseType;
        if (type === 'ROLE_ACTIONS' || type === 'WOLF_YOUNGER_BROTHER_ACTION') {
            setActiveTab('actions');
        } else {
            setActiveTab('werewolves');
        }
    }, [nightStatus?.phaseType]);

    // UI Transformation
    if (!nightStatus) return null;

    const enrichedMessages = nightStatus.werewolfMessages.map(msg => {
        const sender = players.find((p: Player) => p.userId === Number(msg.senderUserId));
        const timestamp = Number(msg.timestamp) < 10000000000 ? Number(msg.timestamp) * 1000 : Number(msg.timestamp);

        return {
            ...msg,
            timestamp,
            senderName: sender?.nickname || `${t('messages.player')} ${msg.senderUserId}`,
            senderUserId: msg.senderUserId,
        };
    });

    const enrichedVotes = useMemo(() => {
        const stateData = gameState?.stateData as any;
        const wolfState = stateData?.wolfStates?.['WEREWOLF_KILL'];
        const electorates = wolfState?.electorates || [];

        // Use electorates if available, otherwise fallback to all alive wolves
        const votingWolfIds = electorates.length > 0
            ? electorates
            : players.filter((p: Player) => p.wolf && p.alive).map(p => p.id);

        return votingWolfIds.map((voterId: number) => {
            const wolf = players.find(p => p.id === voterId);
            const vote = nightStatus.werewolfVotes.find(v => Number(v.voterId) === voterId);
            const rawTargetId = vote?.targetId;
            const targetId = (rawTargetId !== null && rawTargetId !== undefined) ? Number(rawTargetId) : null;
            const target = (targetId !== null && targetId > 0) ? players.find((p: Player) => p.id === targetId) : null;

            let targetName = t('nightStatus.waiting');
            if (targetId === -1) targetName = t('nightStatus.skipped');
            else if (target) targetName = target.nickname;

            return {
                voterId,
                targetId,
                voterName: wolf?.nickname || `${t('messages.player')} ${voterId}`,
                voterUserId: wolf?.userId,
                targetName,
                targetUserId: target?.userId,
                hasVoted: !!vote
            };
        }).sort((a: any, b: any) => Number(a.voterId) - Number(b.voterId));
    }, [players, nightStatus.werewolfVotes, gameState, t]);

    const enrichedStatuses: EnrichedActionStatus[] = (nightStatus.actionStatuses || [])
        .filter(status => !status.actorRole.includes('狼'))
        .map(status => {
            const player = players.find((p: Player) => p.id === Number(status.actor));
            const rawTargetId = status.targets?.[0];
            const targetId = (rawTargetId !== null && rawTargetId !== undefined) ? Number(rawTargetId) : null;
            const target = (targetId !== null && targetId > 0) ? players.find((p: Player) => p.id === targetId) : null;

            let targetName = null;
            if (targetId === -1) targetName = t('nightStatus.skipped');
            else if (target) targetName = target.nickname;
            else if (status.status === 'SUBMITTED') targetName = t('nightStatus.submitted');

            return {
                ...status,
                playerName: player?.nickname || `${t('messages.player')} ${status.actor}`,
                playerUserId: player?.userId,
                targetName,
                targetUserId: target?.userId,
                targetRole: target?.roles?.[0],
                actionName: status.actionDefinitionId ? t(`actions.labels.${status.actionDefinitionId}`) : null
            };
        });

    // Top Hunt Calculation
    const voteCounts: Record<number, number> = {};
    nightStatus.werewolfVotes.forEach(v => {
        if (v.targetId !== null && v.targetId !== undefined) {
            const tid = Number(v.targetId);
            if (!isNaN(tid)) voteCounts[tid] = (voteCounts[tid] || 0) + 1;
        }
    });

    let topTargetId: number | null = null;
    let maxVotes = 0;
    Object.entries(voteCounts).forEach(([tid, count]) => {
        const tId = Number(tid);
        if (count > maxVotes && tId !== 0) {
            maxVotes = count;
            topTargetId = tId;
        }
    });

    const huntTargetPlayer = (topTargetId !== null && topTargetId > 0) ? players.find((p: Player) => p.id === topTargetId) : null;
    const totalWolves = gameState?.stateData?.wolfStates['WEREWOLF_KILL']?.electorates.length || players.filter((p: Player) => p.wolf && p.alive).length;
    const lockThreshold = Math.floor(totalWolves / 2) + 1;
    const votePercentage = Math.round((maxVotes / totalWolves) * 100);

    const getRoleColor = (role: string) => {
        if (role.includes('預')) return 'seer-cyan';
        if (role.includes('女')) return 'witch-purple';
        if (role.includes('守')) return 'guard-green';
        if (role.includes('獵')) return 'hunter-orange';
        return 'primary';
    };

    const getRoleIcon = (role: string) => {
        if (role.includes('預')) return <Eye className="w-5 h-5"/>;
        if (role.includes('女')) return <FlaskConical className="w-5 h-5"/>;
        if (role.includes('守')) return <Shield className="w-5 h-5"/>;
        if (role.includes('獵')) return <Target className="w-5 h-5"/>;
        return <Brain className="w-5 h-5"/>;
    };

    return (
        <div className="text-slate-800 dark:text-white font-['Spline_Sans'] min-h-screen flex flex-col overflow-hidden">
            {/* Top Navigation */}
            <header
                className="h-16 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex items-center px-6 justify-between flex-shrink-0 z-20">
                <div className="flex items-center gap-4">
                    <div className="h-10 w-10 bg-[#3211d4] rounded-lg flex items-center justify-center text-white">
                        <Moon className="w-6 h-6"/>
                    </div>
                    <div>
                        <h1 className="font-bold text-lg tracking-tight">
                            {t(`nightStatus.phases.${nightStatus.phaseType}`)}
                        </h1>
                    </div>
                </div>

                <nav className="hidden md:flex bg-slate-100 dark:bg-slate-800 p-1 rounded-lg">
                    <button
                        onClick={() => setActiveTab('werewolves')}
                        className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === 'werewolves' ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-white shadow-sm' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-white'}`}
                    >
                        {t('nightStatus.tabs.wolfPhase')}
                    </button>
                    <button
                        onClick={() => setActiveTab('actions')}
                        className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${activeTab === 'actions' ? 'bg-white dark:bg-slate-700 text-indigo-600 dark:text-white shadow-sm' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-white'}`}
                    >
                        {t('nightStatus.tabs.actions')}
                    </button>
                </nav>

            </header>

            {/* Main Content Area */}
            <main className="flex-1 overflow-y-auto p-6 relative overflow-x-hidden">

                <div className="max-w-7xl mx-auto relative z-10 space-y-8 h-full">
                    {activeTab === 'werewolves' ? (
                        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 h-full pb-20">
                            {/* LEFT COLUMN: Wolf Channel (Chat) */}
                            <section
                                className="lg:col-span-7 flex flex-col bg-white dark:bg-slate-900 rounded-2xl shadow-xl border border-slate-200 dark:border-slate-800 overflow-hidden h-[600px] animate-in fade-in slide-in-from-left-4 duration-500">
                                {/* Chat Header */}
                                <div
                                    className="p-4 border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-900/50 flex items-center justify-between">
                                    <div
                                        className="flex items-center gap-3 animate-in fade-in slide-in-from-left-4 duration-500 delay-150 fill-mode-both">
                                        <div
                                            className="h-10 w-10 rounded-lg bg-gradient-to-br from-red-600 to-red-900 flex items-center justify-center shadow-inner">
                                            <Users className="w-6 h-6 text-white"/>
                                        </div>
                                        <div>
                                            <h3 className="font-bold text-lg leading-tight">{t('nightStatus.wolfDiscussion')}</h3>
                                            <p className="text-xs text-slate-500 dark:text-slate-400">{t('nightStatus.wolvesOnline', {count: String(totalWolves)})}</p>
                                        </div>
                                    </div>
                                </div>
                                {/* Chat Messages Area */}
                                <div ref={messageScrollContainerRef}
                                     className="flex-1 overflow-y-auto p-4 space-y-6 bg-slate-50 dark:bg-slate-950 scrollbar-hide">
                                    {/* Time Divider */}
                                    <div
                                        className="flex justify-center animate-in fade-in zoom-in-95 duration-500 delay-200 fill-mode-both">
                                        <span
                                            className="text-[10px] font-semibold uppercase tracking-widest text-slate-400 bg-slate-200 dark:bg-[#3211d4]/10 px-3 py-1 rounded-full">{t('nightStatus.nightPhaseStarted')}</span>
                                    </div>

                                    {enrichedMessages.length === 0 ? (
                                        <div
                                            className="flex flex-col items-center justify-center h-full text-slate-500 py-12">
                                            <MessageSquare className="w-12 h-12 mb-2 opacity-20"/>
                                            <p className="text-sm font-medium opacity-50">{t('nightStatus.waitingForWolf')}</p>
                                        </div>
                                    ) : (
                                        <div>
                                            {enrichedMessages.map((msg, index) => {
                                                const isConsecutive = index > 0 && enrichedMessages[index - 1].senderUserId === msg.senderUserId;
                                                const isLastInGroup = index === enrichedMessages.length - 1 || enrichedMessages[index + 1].senderUserId !== msg.senderUserId;

                                                return (
                                                    <div
                                                        key={`${msg.senderUserId}-${msg.timestamp}-${index}`}
                                                        className={`flex gap-4 group animate-in fade-in slide-in-from-left-4 duration-500 fill-mode-both ${isConsecutive ? '-mt-[1px]' : 'mt-6'}`}
                                                        style={{animationDelay: `${250 + Math.min(index * 50, 400)}ms`}}
                                                    >
                                                        <div className="flex-none w-10">
                                                            {!isConsecutive && (
                                                                <div
                                                                    className="h-10 w-10 rounded-full bg-slate-800 border-2 border-slate-700 overflow-hidden relative">
                                                                    <DiscordAvatar
                                                                        userId={String(msg.senderUserId || '')}
                                                                        guildId={guildId}
                                                                        avatarClassName="object-cover w-full h-full"/>
                                                                </div>
                                                            )}
                                                        </div>
                                                        <div className="flex-1">
                                                            {!isConsecutive && (
                                                                <div className="flex items-baseline gap-2 mb-1">
                                                                    <span
                                                                        className="font-semibold text-sm text-red-600 dark:text-red-400">
                                                                        <DiscordName
                                                                            userId={String(msg.senderUserId || '')}
                                                                            guildId={guildId}
                                                                            fallbackName={msg.senderName || ''}/>
                                                                    </span>
                                                                    <span className="text-[10px] text-slate-500">
                                                                        {new Date(msg.timestamp).toLocaleTimeString([], {
                                                                            hour: '2-digit',
                                                                            minute: '2-digit',
                                                                            hour12: false
                                                                        })}
                                                                    </span>
                                                                </div>
                                                            )}
                                                            <div
                                                                className={`bg-white dark:bg-slate-800 p-3 shadow-sm inline-block max-w-[90%] text-sm leading-relaxed text-slate-700 dark:text-slate-200 border border-slate-200 dark:border-slate-700 ${!isConsecutive ? 'rounded-t-2xl rounded-br-2xl rounded-bl-sm' : isLastInGroup ? 'rounded-b-2xl rounded-tr-2xl rounded-tl-sm' : 'rounded-r-2xl rounded-l-sm'}`}>
                                                                {msg.content}
                                                            </div>
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}
                                </div>
                            </section>

                            {/* RIGHT COLUMN: Voting Results */}
                            <section
                                className="lg:col-span-5 flex flex-col gap-6 h-[600px] animate-in fade-in slide-in-from-right-4 duration-500">
                                {/* Target Summary Card */}
                                <div
                                    className="bg-gradient-to-br from-[#3211d4] to-[#200a8a] rounded-2xl p-6 shadow-xl text-white relative overflow-hidden animate-in fade-in zoom-in-95 duration-700 fill-mode-both">
                                    <div
                                        className="absolute top-0 right-0 opacity-10 transform translate-x-1/4 -translate-y-1/4">
                                        <Target className="w-44 h-44"/>
                                    </div>
                                    <div className="relative z-10">
                                        <h2 className="text-white/70 text-sm uppercase tracking-wider font-semibold mb-2 animate-in fade-in slide-in-from-top-2 duration-500 delay-100 fill-mode-both">{t('nightStatus.wolfTarget')}</h2>
                                        <div className="flex items-center gap-4 mb-6">
                                            <div
                                                className="h-16 w-16 rounded-full border-4 border-white/20 overflow-hidden shadow-lg bg-slate-800 animate-in fade-in zoom-in-75 duration-500 delay-200 fill-mode-both transition-transform hover:scale-105">
                                                {topTargetId && topTargetId > 0 ? (
                                                    <DiscordAvatar userId={String(huntTargetPlayer?.userId ?? '')}
                                                                   guildId={guildId}
                                                                   avatarClassName="object-cover w-full h-full"/>
                                                ) : topTargetId === -1 ? (
                                                    <div
                                                        className="w-full h-full flex items-center justify-center bg-amber-600">
                                                        <Ban className="w-8 h-8 text-white"/>
                                                    </div>
                                                ) : (
                                                    <div
                                                        className="w-full h-full flex items-center justify-center bg-slate-700">
                                                        <User className="w-8 h-8 opacity-20"/>
                                                    </div>
                                                )}
                                            </div>
                                            <div
                                                className="animate-in fade-in slide-in-from-left-4 duration-500 delay-300 fill-mode-both">
                                                <h3 className="text-3xl font-bold flex items-center">
                                                    {topTargetId === -1 ? (
                                                        <span
                                                            className="bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 px-3 py-1 rounded-lg text-lg border border-amber-200 dark:border-amber-800 flex items-center gap-2">
                                                            <Ban className="w-5 h-5"/>
                                                            {t('nightStatus.targetSkipped')}
                                                        </span>
                                                    ) : (huntTargetPlayer?.nickname || t('roles.unknown'))}
                                                </h3>
                                                <div className="flex items-center gap-2 text-white/70">
                                                    <AlertCircle className="w-4 h-4"/>
                                                    <span
                                                        className="text-sm font-medium">{topTargetId ? t('nightStatus.atRisk') : t('nightStatus.undecided')}</span>
                                                </div>
                                            </div>
                                        </div>
                                        {/* Progress Bar */}
                                        <div
                                            className="space-y-2 animate-in fade-in slide-in-from-bottom-2 duration-500 delay-400 fill-mode-both">
                                            <div className="flex justify-between text-sm font-medium">
                                                <span>{t('nightStatus.votesRatio', {
                                                    current: String(maxVotes),
                                                    total: String(totalWolves)
                                                })}</span>
                                                <span>{votePercentage}%</span>
                                            </div>
                                            <div className="h-3 w-full bg-black/20 rounded-full overflow-hidden">
                                                <div
                                                    className="h-full bg-white rounded-full shadow-[0_0_10px_rgba(255,255,255,0.5)] transition-all duration-1000 ease-out"
                                                    style={{width: `${votePercentage}%`}}
                                                ></div>
                                            </div>
                                            <p className="text-xs text-white/60 mt-2">
                                                {maxVotes >= lockThreshold ? t('nightStatus.targetLocked') : t('nightStatus.moreVotesNeeded', {count: String(lockThreshold - maxVotes)})}
                                            </p>
                                        </div>
                                    </div>
                                </div>
                                {/* Live Feed Panel */}
                                <div
                                    className="flex-1 bg-white dark:bg-slate-900 rounded-2xl shadow-xl border border-slate-200 dark:border-slate-800 flex flex-col overflow-hidden animate-in fade-in slide-in-from-bottom-4 duration-500 fill-mode-both"
                                    style={{animationDelay: '150ms'}}>
                                    <div
                                        className="p-4 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between bg-slate-50 dark:bg-slate-900/50">
                                        <h3 className="font-bold text-lg">{t('nightStatus.wolfVotes')}</h3>
                                    </div>
                                    <div className="flex-1 overflow-y-auto p-4 space-y-3 relative">
                                        <div
                                            className="absolute left-[27px] top-4 bottom-4 w-px bg-slate-200 dark:bg-slate-700 z-0"></div>

                                        {enrichedVotes.length === 0 ? (
                                            <div
                                                className="flex items-center justify-center h-full text-slate-400 text-sm">{t('nightStatus.waitingForVotes')}</div>
                                        ) : (
                                            enrichedVotes.map((vote: any, idx: number) => {
                                                const isSkipped = vote.targetId === -1;
                                                const isVoted = vote.hasVoted && vote.targetId !== null && !isSkipped;
                                                const isPending = !vote.hasVoted || (vote.hasVoted && vote.targetId === null);

                                                return (
                                                    <div
                                                        key={idx}
                                                        className={`relative z-10 flex items-center gap-3 transition-opacity duration-300 animate-in fade-in slide-in-from-right-4 fill-mode-both ${isPending ? 'opacity-60' : 'opacity-100'}`}
                                                        style={{animationDelay: `${200 + idx * 50}ms`}}
                                                    >
                                                        <div
                                                            className={`h-6 w-6 rounded-full flex items-center justify-center border-2 border-[#f6f6f8] dark:border-[#1c1836] shadow-sm flex-none transition-colors duration-300 ${isVoted ? 'bg-green-500' : isSkipped ? 'bg-amber-500' : 'bg-slate-400 animate-pulse'}`}>
                                                            {isVoted ?
                                                                <Check className="w-3 h-3 text-white"/> : isSkipped ?
                                                                    <Ban className="w-3 h-3 text-white"/> :
                                                                    <Clock className="w-3 h-3 text-white"/>}
                                                        </div>
                                                        <div
                                                            className={`flex-1 bg-slate-50 dark:bg-slate-900/50 border p-3 rounded-lg flex justify-between items-center group transition-all duration-300 ${isPending ? 'border-dashed border-slate-300 dark:border-slate-700' : 'border-slate-200 dark:border-slate-800 hover:bg-slate-100 dark:hover:bg-slate-800'}`}>
                                                            <div className="flex items-center gap-2">
                                                                <DiscordAvatar userId={String(vote.voterUserId || '')}
                                                                               guildId={guildId}
                                                                               avatarClassName="h-6 w-6 rounded-full object-cover"/>
                                                                <span
                                                                    className={`text-sm font-medium ${isVoted ? 'text-red-400' : 'text-slate-500'}`}>
                                                                    <DiscordName userId={String(vote.voterUserId || '')}
                                                                                 guildId={guildId}
                                                                                 fallbackName={vote.voterName || ''}/>
                                                                </span>
                                                            </div>
                                                            <ArrowRight
                                                                className={`w-4 h-4 transition-colors ${isVoted ? 'text-slate-400' : 'text-slate-300'}`}/>
                                                            <div className="flex items-center gap-2">
                                                                <span
                                                                    className={`text-sm font-bold ${isVoted ? 'text-slate-700 dark:text-white' : 'text-slate-400 italic font-normal text-xs'}`}>
                                                                    {vote.targetName}
                                                                </span>
                                                                {!!vote.targetUserId && isVoted && (
                                                                    <DiscordAvatar userId={String(vote.targetUserId)}
                                                                                   guildId={guildId}
                                                                                   avatarClassName="h-6 w-6 rounded-full object-cover grayscale group-hover:grayscale-0 transition-all"/>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </div>
                                                );
                                            })
                                        )}
                                    </div>
                                </div>
                            </section>
                        </div>
                    ) : (
                        /* Role Actions Screen (Redesigned) */
                        <div className="space-y-8 pb-32 animate-in fade-in duration-500">
                            {/* Wolf Kill Summary Banner */}
                            <section
                                className="bg-red-50/50 dark:bg-slate-900/70 backdrop-blur-xl rounded-xl overflow-hidden shadow-lg border border-red-200 dark:border-red-500/30 relative animate-in fade-in slide-in-from-top-4 duration-700 fill-mode-both">
                                <div
                                    className="absolute inset-0 bg-gradient-to-r from-red-500/10 via-transparent to-transparent"></div>
                                <div className="p-6 md:flex md:items-center md:justify-between relative">
                                    <div className="flex items-center gap-6">
                                        <div
                                            className={`h-16 w-16 rounded-full flex items-center justify-center border shadow-[0_0_15px_rgba(239,68,68,0.3)] animate-in fade-in zoom-in-75 duration-500 delay-100 fill-mode-both overflow-hidden ${topTargetId === -1 ? 'bg-amber-500/20 border-amber-500/50' : 'bg-red-500/20 border-red-500/50'}`}>
                                            {topTargetId && topTargetId > 0 ? (
                                                <DiscordAvatar
                                                    userId={String(huntTargetPlayer?.userId ?? '')}
                                                    guildId={guildId}
                                                    avatarClassName="object-cover w-full h-full"
                                                />
                                            ) : topTargetId === -1 ? (
                                                <Ban className="text-amber-500 w-8 h-8"/>
                                            ) : (
                                                <Lightbulb className="text-red-500 w-8 h-8"/>
                                            )}
                                        </div>
                                        <div
                                            className="animate-in fade-in slide-in-from-left-4 duration-500 delay-200 fill-mode-both">
                                            <h2 className="text-red-500 font-bold text-sm tracking-widest uppercase mb-1">{t('nightStatus.wolfKillConsensus')}</h2>
                                            <div className="flex items-baseline gap-3">
                                                <span
                                                    className="text-3xl font-bold text-slate-900 dark:text-white flex items-center">
                                                    {topTargetId === -1 ? (
                                                        <span
                                                            className="bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 px-3 py-1 rounded-lg text-lg border border-amber-200 dark:border-amber-800 flex items-center gap-2">
                                                            <Ban className="w-5 h-5"/>
                                                            {t('nightStatus.targetSkipped')}
                                                        </span>
                                                    ) : (huntTargetPlayer?.nickname || t('nightStatus.thinking'))}
                                                </span>
                                            </div>
                                            <div className="flex items-center gap-2 mt-2">
                                                <span className="text-xs text-slate-400">{t('nightStatus.votesCount', {
                                                    current: String(maxVotes),
                                                    total: String(totalWolves)
                                                })}</span>
                                                <div className="w-32 h-1.5 bg-slate-700 rounded-full overflow-hidden">
                                                    <div
                                                        className="h-full bg-red-500 transition-all duration-1000 ease-out"
                                                        style={{width: `${votePercentage}%`}}></div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="mt-6 md:mt-0 flex items-center gap-4">
                                        <div className="flex -space-x-3">
                                            {enrichedVotes.slice(0, 3).map((v: any, i: number) => (
                                                <div key={i}
                                                     className="w-10 h-10 rounded-full border-2 border-white dark:border-slate-900 overflow-hidden shadow-lg animate-in fade-in slide-in-from-right-4 duration-500 fill-mode-both"
                                                     style={{animationDelay: `${200 + i * 80}ms`}}>
                                                    <DiscordAvatar userId={String(v.voterUserId || '')}
                                                                   guildId={guildId}
                                                                   avatarClassName="w-full h-full object-cover"/>
                                                </div>
                                            ))}
                                            {totalWolves > 3 && (
                                                <div
                                                    className="w-10 h-10 rounded-full bg-slate-100 dark:bg-slate-800 border-2 border-white dark:border-slate-900 flex items-center justify-center text-[10px] text-slate-500 dark:text-slate-400 animate-in fade-in slide-in-from-right-4 duration-500 fill-mode-both"
                                                    style={{animationDelay: `${200 + Math.min(enrichedVotes.length, 3) * 80}ms`}}>
                                                    +{totalWolves - 3}
                                                </div>
                                            )}
                                        </div>
                                        <div className="text-right">
                                            <div
                                                className="text-2xl font-mono text-slate-900 dark:text-white font-bold">
                                                {String(Math.floor(getRemainingSeconds() / 60)).padStart(2, '0')}:
                                                {String(getRemainingSeconds() % 60).padStart(2, '0')}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </section>

                            <div className="space-y-4">
                                <div className="flex items-center justify-between">
                                    <h3 className="text-xl font-bold text-slate-900 dark:text-white flex items-center gap-2">
                                        <Brain className="text-[#3211d4]"/>
                                        {t('nightStatus.specialRoleActions')}
                                    </h3>
                                    <div className="flex items-center gap-4">
                                        <span className="text-sm text-slate-400">
                                            {t('nightStatus.actionsCompleted', {
                                                completed: String(enrichedStatuses.filter(s => s.status === 'SUBMITTED').length),
                                                total: String(enrichedStatuses.length)
                                            })}
                                        </span>
                                        {enrichedStatuses.length > 0 && (
                                            <div
                                                className="w-32 h-2 bg-slate-800 rounded-full overflow-hidden border border-slate-700/50">
                                                <div
                                                    className="h-full bg-[#3211d4] rounded-full transition-all duration-700 ease-out shadow-[0_0_10px_rgba(50,17,212,0.5)]"
                                                    style={{width: `${Math.round((enrichedStatuses.filter(s => s.status === 'SUBMITTED').length / enrichedStatuses.length) * 100)}%`}}
                                                ></div>
                                            </div>
                                        )}
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                                    {enrichedStatuses.map((status, index) => {
                                        const roleColor = getRoleColor(status.actorRole);
                                        const isActing = status.status === 'ACTING';
                                        const isSkipped = status.status === 'SKIPPED';
                                        const isSubmitted = status.status === 'SUBMITTED';

                                        // Explicit style mapping for Tailwind
                                        const borderColorClass =
                                            roleColor === 'seer-cyan' ? '!border-l-[#06b6d4]' :
                                                roleColor === 'witch-purple' ? '!border-l-[#a855f7]' :
                                                    roleColor === 'guard-green' ? '!border-l-[#10b981]' :
                                                        roleColor === 'hunter-orange' ? '!border-l-[#f97316]' :
                                                            '!border-l-[#3211d4]';

                                        const bgColorClass =
                                            roleColor === 'seer-cyan' ? 'bg-cyan-100 dark:bg-[#06b6d4]/20 text-cyan-700 dark:text-[#06b6d4]' :
                                                roleColor === 'witch-purple' ? 'bg-purple-100 dark:bg-[#a855f7]/20 text-purple-700 dark:text-[#a855f7]' :
                                                    roleColor === 'guard-green' ? 'bg-emerald-100 dark:bg-[#10b981]/20 text-emerald-700 dark:text-[#10b981]' :
                                                        roleColor === 'hunter-orange' ? 'bg-orange-100 dark:bg-[#f97316]/20 text-orange-700 dark:text-[#f97316]' :
                                                            'bg-indigo-100 dark:bg-[#3211d4]/20 text-indigo-700 dark:text-[#3211d4]';

                                        const glowClass =
                                            roleColor === 'seer-cyan' ? 'shadow-[0_0_20px_rgba(6,182,212,0.3)] ring-[#06b6d4]/40' :
                                                roleColor === 'witch-purple' ? 'shadow-[0_0_20px_rgba(168,85,247,0.3)] ring-[#a855f7]/40' :
                                                    roleColor === 'guard-green' ? 'shadow-[0_0_20px_rgba(16,185,129,0.3)] ring-[#10b981]/40' :
                                                        roleColor === 'hunter-orange' ? 'shadow-[0_0_20px_rgba(249,115,22,0.3)] ring-[#f97316]/40' :
                                                            'shadow-[0_0_20px_rgba(50,17,212,0.3)] ring-[#3211d4]/40';

                                        return (
                                            <div
                                                key={`${status.actor}-${index}`}
                                                className={`bg-white dark:bg-slate-900 rounded-xl border-l-4 border-t border-r border-b border-slate-200 dark:border-slate-800 overflow-hidden group transition-all duration-300 animate-in fade-in slide-in-from-left-4 fill-mode-both ${isActing ? `ring-1 ${glowClass}` : 'shadow-lg'} ${isSkipped ? 'opacity-75 grayscale border-l-slate-500' : borderColorClass}`}
                                                style={{animationDelay: `${250 + index * 100}ms`}}
                                            >
                                                <div className="p-5 relative z-10">
                                                    <div className="flex justify-between items-start mb-6">
                                                        <div className="flex items-center gap-3">
                                                            <div
                                                                className={`h-10 w-10 rounded-lg flex items-center justify-center ${isSkipped ? 'bg-slate-500/20 text-slate-500' : bgColorClass}`}>
                                                                {getRoleIcon(status.actorRole)}
                                                            </div>
                                                            <div>
                                                                <h4 className="font-bold text-slate-900 dark:text-white">{status.actorRole}</h4>
                                                                <div className="flex flex-col">
                                                                    {status.actionName && (
                                                                        <span
                                                                            className={`text-[10px] font-bold mt-1 px-1.5 py-0.5 rounded ${bgColorClass}`}>
                                                                            {status.actionName}
                                                                        </span>
                                                                    )}
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <span
                                                            className={`px-2.5 py-1 rounded text-[10px] font-bold tracking-wide uppercase border ${isSubmitted ? 'bg-green-500/10 text-green-500 border-green-500/20' :
                                                                isActing ? 'bg-[#3211d4]/10 dark:bg-[#3211d4]/20 text-[#3211d4] dark:text-white border-[#3211d4]/30 dark:border-[#3211d4]/50 animate-pulse' :
                                                                    isSkipped ? 'bg-amber-500/10 text-amber-600 dark:text-amber-500 border-amber-500/20' :
                                                                        'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-600'
                                                            }`}>
                                                            {status.status ? t(`nightStatus.${status.status.toLowerCase()}`) : status.status}
                                                        </span>
                                                    </div>

                                                    <div
                                                        className="flex items-center justify-between bg-slate-50 dark:bg-black/20 rounded-lg p-4 relative min-h-[100px] border border-slate-100 dark:border-transparent">
                                                        {isSkipped ? (
                                                            <div className="flex flex-col items-center gap-2 w-full">
                                                                <Ban className="text-slate-600 w-8 h-8"/>
                                                                <span
                                                                    className="text-xs text-slate-500">{t('nightStatus.noActionTaken')}</span>
                                                            </div>
                                                        ) : (
                                                            <>
                                                                <div
                                                                    className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-16 h-[2px] bg-gradient-to-r from-white/10 to-white/30 z-0"></div>
                                                                <div
                                                                    className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-10 bg-white dark:bg-slate-900 p-1 rounded-full border border-slate-200 dark:border-slate-800 shadow-sm">
                                                                    <ArrowRight className="text-slate-500 w-4 h-4"/>
                                                                </div>

                                                                <div className="relative z-10 text-center">
                                                                    <div
                                                                        className={`w-12 h-12 rounded-full border-2 p-0.5 mx-auto mb-2 ${isSkipped ? 'border-slate-500/50' : borderColorClass.replace('border-l-', 'border-')}/50`}>
                                                                        <DiscordAvatar
                                                                            userId={String(status.playerUserId || '')}
                                                                            guildId={guildId}
                                                                            avatarClassName="w-full h-full rounded-full object-cover"/>
                                                                    </div>
                                                                    <span
                                                                        className="text-[10px] font-bold text-slate-400 truncate max-w-[60px] block">
                                                                        <DiscordName
                                                                            userId={String(status.playerUserId || '')}
                                                                            guildId={guildId}
                                                                            fallbackName={status.playerName}/>
                                                                    </span>
                                                                </div>

                                                                <div className="relative z-10 text-center">
                                                                    <div
                                                                        className="w-12 h-12 rounded-full border-2 border-slate-600 p-0.5 mx-auto mb-2 relative group-hover:border-white/50 transition-colors duration-300">
                                                                        {status.targetUserId ? (
                                                                            <DiscordAvatar
                                                                                userId={String(status.targetUserId)}
                                                                                guildId={guildId}
                                                                                avatarClassName="w-full h-full rounded-full object-cover"/>
                                                                        ) : (
                                                                            <div
                                                                                className="w-full h-full rounded-full bg-slate-800 flex items-center justify-center">
                                                                                <User
                                                                                    className="text-slate-600 w-6 h-6"/>
                                                                            </div>
                                                                        )}
                                                                        {isSubmitted && (
                                                                            <div
                                                                                className="absolute -top-1 -right-1 h-5 w-5 bg-white dark:bg-slate-900 rounded-full flex items-center justify-center border border-slate-200 dark:border-slate-800">
                                                                                <Check
                                                                                    className="text-green-500 w-3 h-3"/>
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                    <div className="flex flex-col">
                                                                        <span
                                                                            className="text-xs font-bold text-slate-900 dark:text-white truncate max-w-[80px]">
                                                                            {status.targetUserId ? (
                                                                                <DiscordName
                                                                                    userId={String(status.targetUserId)}
                                                                                    guildId={guildId}
                                                                                    fallbackName={status.targetName || ''}/>
                                                                            ) : (status.targetName || (isActing ? t('nightStatus.thinking') : t('nightStatus.waiting')))}
                                                                        </span>
                                                                        <span
                                                                            className="text-[10px] text-[#3211d4] font-bold uppercase">{status.targetRole}</span>
                                                                    </div>
                                                                </div>
                                                            </>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>

                        </div>
                    )}
                </div>
            </main>
        </div>
    );
};
