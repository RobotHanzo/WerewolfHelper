import React, {useEffect, useState} from 'react';
import {ChevronRight, Clock, FastForward, MessageCircle, Skull, Users} from 'lucide-react';
import {GameStateDto as GameState, Player, RoleActionInstance} from '@/api/types.gen';
import {DiscordAvatar, DiscordName} from '@/components/DiscordUser';
import {useTranslation} from '@/lib/i18n';

// Local types for enriched data display
export type ActionStatusType = RoleActionInstance['status'];

interface EnrichedActionStatus extends RoleActionInstance {
    playerName: string;
    playerUserId?: string | number | bigint;
    targetName: string | null;
    targetUserId?: string | number | bigint;
}

interface RoleActionsScreenProps {
    statuses: EnrichedActionStatus[];
    wolfTargetName: string;
    wolfTargetUserId?: string | number | bigint;
    wolfTargetId: number | null;
    wolfVoteCount: number;
    guildId?: string | number | bigint;
}

const RoleActionsScreen: React.FC<RoleActionsScreenProps> = ({
                                                                 statuses,
                                                                 wolfTargetName,
                                                                 wolfTargetUserId,
                                                                 wolfTargetId,
                                                                 wolfVoteCount,
                                                                 guildId
                                                             }) => {
    const {t} = useTranslation();

    // Deduplicate statuses by actor to handle potential backend data issues
    const uniqueStatuses = React.useMemo(() => {
        const map = new Map<number, EnrichedActionStatus>();
        statuses.forEach(s => map.set(s.actor, s));
        return Array.from(map.values());
    }, [statuses]);

    const getRoleBorderColor = (role: string): string => {
        if (role.includes('狼')) return 'border-red-500/50 hover:border-red-500';
        if (role.includes('女巫')) return 'border-purple-500/50 hover:border-purple-500';
        if (role.includes('獵人')) return 'border-amber-500/50 hover:border-amber-500';
        if (role.includes('預言家')) return 'border-blue-500/50 hover:border-blue-500';
        if (role.includes('守衛')) return 'border-cyan-500/50 hover:border-cyan-500';
        return 'border-slate-700 hover:border-slate-500';
    };

    const getStatusColor = (status: ActionStatusType): string => {
        switch (status) {
            case 'SUBMITTED':
                return 'bg-green-500/20 text-green-300 border-green-500';
            case 'SKIPPED':
                return 'bg-amber-500/20 text-amber-300 border-amber-500';
            case 'ACTING':
                return 'bg-blue-500/20 text-blue-300 border-blue-500';
            case 'PROCESSED':
                return 'bg-indigo-500/20 text-indigo-300 border-indigo-500';
            default:
                return 'bg-slate-500/20 text-slate-300 border-slate-500';
        }
    };

    return (
        <div className="flex flex-col h-full">
            {/* Wolf Kill Summary */}
            <div
                className="animate-slide-in mb-3 bg-red-900/20 border border-red-800/50 rounded-lg p-3 flex items-center justify-between flex-shrink-0"
                style={{animationDelay: '50ms'}}
            >
                <div className="flex items-center gap-3">
                    <div className="bg-red-600/20 p-2 rounded-full">
                        <Skull className="w-5 h-5 text-red-500"/>
                    </div>
                    <div>
                        <div className="text-xs text-red-300 font-bold uppercase tracking-wider">狼人擊殺目標</div>
                        <div className="font-bold text-lg text-white flex items-center gap-2">
                            {wolfTargetName}
                            {wolfVoteCount > 0 && <span
                                className="text-xs bg-red-600 px-1.5 py-0.5 rounded-full">{wolfVoteCount} 票</span>}
                        </div>
                    </div>
                </div>
                {wolfTargetId === -1 ? (
                    <div
                        className="w-10 h-10 rounded-full border-2 border-amber-500/50 bg-amber-500/20 flex items-center justify-center">
                        <FastForward className="w-5 h-5 text-amber-500"/>
                    </div>
                ) : (
                    <DiscordAvatar userId={wolfTargetUserId} guildId={guildId}
                                   avatarClassName="w-10 h-10 rounded-full border-2 border-red-600/50"/>
                )}
            </div>

            {/* Grid of actions */}
            <div className="grid grid-cols-2 gap-3 overflow-y-auto p-2 min-h-0">
                {uniqueStatuses.length === 0 ? (
                    <div className="col-span-2 flex items-center justify-center h-full text-slate-400">
                        等待職業行動...
                    </div>
                ) : (
                    uniqueStatuses.map((status, index) => (
                        <div
                            key={status.actor}
                            className={`animate-slide-in bg-slate-800/80 rounded-lg p-4 border-2 ${getRoleBorderColor(status.actorRole)} transition-all duration-300 flex flex-col justify-between shadow-lg`}
                            style={{animationDelay: `${100 + index * 75}ms`}}
                        >
                            <div className="flex items-start justify-between mb-3">
                                <div className="min-w-0">
                                    <h3 className="font-bold text-lg leading-tight truncate">
                                        <DiscordName userId={status.playerUserId} guildId={guildId}
                                                     fallbackName={status.playerName}/>
                                    </h3>
                                    <p className="text-xs opacity-70 mt-0.5">{status.actorRole}</p>
                                </div>
                                <DiscordAvatar userId={status.playerUserId} guildId={guildId}
                                               avatarClassName="w-10 h-10 rounded-full border border-slate-700 shadow-sm flex-shrink-0 ml-3"/>
                            </div>

                            <div className="space-y-2">
                                {status.actionDefinitionId && (
                                    <div className="bg-white/5 rounded px-2 py-1">
                                        <p className="text-xs opacity-60">行動</p>
                                        <p className="font-semibold text-slate-200">
                                            {t(`actions.labels.${status.actionDefinitionId}`, {defaultValue: status.actionDefinitionId})}
                                        </p>
                                    </div>
                                )}

                                {status.targetName && (
                                    <div
                                        className="bg-white/10 rounded px-2 py-1 flex items-center justify-between gap-2">
                                        <div className="min-w-0">
                                            <p className="text-[10px] opacity-60 uppercase font-bold tracking-tight">目標</p>
                                            <p className="font-semibold text-sm truncate">
                                                <DiscordName userId={status.targetUserId} guildId={guildId}
                                                             fallbackName={status.targetName || ''}/>
                                            </p>
                                        </div>
                                        {status.targets?.[0] === -1 ? (
                                            <div
                                                className="w-6 h-6 rounded-full border border-amber-500/50 bg-amber-500/20 flex items-center justify-center flex-shrink-0">
                                                <FastForward className="w-3 h-3 text-amber-500"/>
                                            </div>
                                        ) : (
                                            <DiscordAvatar userId={status.targetUserId} guildId={guildId}
                                                           avatarClassName="w-6 h-6 rounded-full border border-slate-600 flex-shrink-0"/>
                                        )}
                                    </div>
                                )}

                                <div
                                    className={`border rounded px-2 py-1 text-xs font-semibold text-center ${getStatusColor(status.status)}`}>
                                    {status.status === 'SUBMITTED'
                                        ? '✓ 已提交'
                                        : status.status === 'SKIPPED'
                                            ? '⏭️ 已跳過'
                                            : status.status === 'ACTING'
                                                ? '⚡ 行動中...'
                                                : status.status === 'PROCESSED'
                                                    ? '✨ 已處理'
                                                    : '⏳ 待提交'}
                                </div>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
};

interface NightStatusProps {
    guildId?: string;
    players?: Player[];
    gameState?: GameState;
}

interface WerewolfMessage {
    senderId: number;
    senderName?: string;
    content: string;
    timestamp: number;
    senderUserId?: string | number | bigint;
}

interface WerewolfVote {
    voterId: number;
    targetId: number | string | null;
    voterName?: string;
    voterUserId?: string | number | bigint;
    targetName?: string;
    targetUserId?: string | number | bigint;
}

interface NightStatusData {
    day: number;
    phaseType: 'WEREWOLF_VOTING' | 'ROLE_ACTIONS' | 'WOLF_YOUNGER_BROTHER_ACTION';
    startTime: number;
    endTime: number;
    werewolfMessages: WerewolfMessage[];
    werewolfVotes: WerewolfVote[];
    actionStatuses: RoleActionInstance[];
}

export const NightStatus: React.FC<NightStatusProps> = ({guildId: propGuildId, players = [], gameState}) => {
    const [activeTab, setActiveTab] = useState<'werewolves' | 'actions'>('werewolves');
    const messageScrollContainerRef = React.useRef<HTMLDivElement>(null);

    // Get guildId from URL or props
    const pathParts = typeof window !== 'undefined' ? window.location.pathname.split('/') : [];
    const guildId = propGuildId || pathParts[2];

    const nightStatus = React.useMemo<NightStatusData | null>(() => {
        if (!gameState?.stateData) return null;

        const stateData = gameState.stateData as any;
        const phaseType = stateData.phaseType || 'WEREWOLF_VOTING';

        // Map werewolf messages
        const werewolfMessages = (stateData.werewolfMessages || []).map((msg: any) => {
            const sender = players.find(p => p.id === msg.senderId);
            return {
                senderId: msg.senderId,
                senderName: sender?.nickname,
                senderUserId: sender?.userId,
                content: msg.content,
                timestamp: msg.timestamp
            };
        });

        // Map werewolf votes
        const wolfState = stateData.wolfStates?.['WEREWOLF_KILL'];
        const werewolfVotes = (wolfState?.votes || []).map((vote: any) => ({
            voterId: vote.voterId,
            targetId: vote.targetId
        }));

        // Map action statuses
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

    // Calculate remaining time based on phase start time and phase type
    const getRemainingSeconds = (): number => {
        if (!nightStatus) return 0;
        return Math.max(0, Math.floor((nightStatus.endTime - Date.now()) / 1000));
    };

    // Auto-scroll messages to bottom
    useEffect(() => {
        if (messageScrollContainerRef.current) {
            messageScrollContainerRef.current.scrollTop = messageScrollContainerRef.current.scrollHeight;
        }
    }, [nightStatus?.werewolfMessages]);

    // Auto-switch tab when phase changes
    useEffect(() => {
        if (nightStatus?.phaseType === 'ROLE_ACTIONS' || nightStatus?.phaseType === 'WOLF_YOUNGER_BROTHER_ACTION') {
            setActiveTab('actions');
        }
    }, [nightStatus?.phaseType]);

    if (!nightStatus) {
        return null;
    }

    const enrichedMessages = nightStatus.werewolfMessages.map(msg => {
        const sender = players.find(p => p.id === Number(msg.senderId));
        return {
            ...msg,
            senderName: msg.senderName || sender?.nickname || `玩家 ${msg.senderId}`,
            senderUserId: sender?.userId || undefined,
        };
    });

    const enrichedVotes = nightStatus.werewolfVotes.map(vote => {
        const voter = players.find(p => p.id === Number(vote.voterId));
        const rawTargetId = vote.targetId;
        const targetId = (rawTargetId !== null && rawTargetId !== undefined) ? Number(rawTargetId) : null;
        const target = (targetId !== null && targetId > 0) ? players.find(p => p.id === targetId) : null;

        let targetName = '未投票';
        if (targetId === -1) {
            targetName = '跳過';
        } else if (target) {
            targetName = target.nickname;
        } else if (targetId !== null && targetId > 0) {
            targetName = `玩家 ${targetId}`;
        }

        return {
            ...vote,
            targetId,
            voterName: voter?.nickname || `玩家 ${vote.voterId}`,
            voterUserId: voter?.userId || undefined,
            targetName,
            targetUserId: target?.userId || undefined,
        };
    }).sort((a, b) => Number(a.voterId) - Number(b.voterId));

    // Filter out wolf actions from the main list
    const enrichedStatuses: EnrichedActionStatus[] = (nightStatus.actionStatuses || [])
        .filter(status => !status.actorRole.includes('狼'))
        .map(status => {
            const player = players.find(p => p.id === Number(status.actor));
            const rawTargetId = status.targets?.[0];
            const targetId = (rawTargetId !== null && rawTargetId !== undefined) ? Number(rawTargetId) : null;
            const target = (targetId !== null && targetId > 0) ? players.find(p => p.id === targetId) : null;

            let targetName = null;
            if (targetId === -1) {
                targetName = '跳過';
            } else if (target) {
                targetName = target.nickname;
            } else if (targetId !== null && targetId > 0) {
                targetName = `玩家 ${targetId}`;
            } else if (status.status === 'SUBMITTED') {
                targetName = '已提交';
            }

            return {
                ...status,
                playerName: player?.nickname || `玩家 ${status.actor}`,
                playerUserId: player?.userId || undefined,
                targetName,
                targetUserId: target?.userId || undefined,
            };
        });

    // Calculate wolf kill target
    const wolfVotes = nightStatus.werewolfVotes;
    const voteCounts: Record<number, number> = {};
    wolfVotes.forEach(v => {
        if (v.targetId !== null && v.targetId !== undefined) {
            const tid = Number(v.targetId);
            if (!isNaN(tid) && tid !== 0) {
                voteCounts[tid] = (voteCounts[tid] || 0) + 1;
            }
        }
    });

    let topTargetId: number | null = null;
    let maxVotes = 0;
    Object.entries(voteCounts).forEach(([tid, count]) => {
        const targetId = Number(tid);
        if (count > maxVotes) {
            maxVotes = count;
            topTargetId = targetId;
        }
    });

    const topTarget = (topTargetId !== null && topTargetId > 0) ? players.find(p => p.id === topTargetId) : null;
    let wolfTargetName = '未定';

    if (maxVotes === 0) {
        wolfTargetName = '未定';
    } else if (topTargetId === -1) {
        wolfTargetName = '跳過';
    } else if (topTarget) {
        wolfTargetName = topTarget.nickname;
    } else if (topTargetId !== null && topTargetId > 0) {
        wolfTargetName = `玩家 ${topTargetId}`;
    }
    const wolfTargetUserId = topTarget?.userId || undefined;

    return (
        <div className="w-full h-full bg-slate-900 text-white p-4 overflow-hidden flex flex-col">
            <style>
                {`@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
                @keyframes slideIn { from { opacity: 0; transform: translateX(-10px); } to { opacity: 1; transform: translateX(0); } }
                .animate-fade-in { animation: fadeIn 0.3s ease-in-out; }
                .animate-slide-in { animation: slideIn 0.3s ease-out; animation-fill-mode: backwards; }
                .message-scroll { scrollbar-width: thin; scrollbar-color: rgba(100, 116, 139, 0.5) transparent; }
                .message-scroll::-webkit-scrollbar { width: 6px; }
                .message-scroll::-webkit-scrollbar-track { background: transparent; }
                .message-scroll::-webkit-scrollbar-thumb { background-color: rgba(100, 116, 139, 0.5); border-radius: 3px; }
                .message-scroll::-webkit-scrollbar-thumb:hover { background-color: rgba(100, 116, 139, 0.7); }`}
            </style>
            <div className="mb-4">
                <div className="flex items-center justify-between mb-3">
                    <h1 className="text-2xl font-bold flex items-center gap-2">
                        <Clock className="w-6 h-6 text-cyan-400"/>
                        第 {nightStatus.day} 晚
                    </h1>
                    <div className="text-3xl font-bold font-mono text-cyan-400">
                        {String(Math.floor(getRemainingSeconds() / 60)).padStart(2, '0')}:
                        {String(getRemainingSeconds() % 60).padStart(2, '0')}
                    </div>
                </div>

                <div className="flex gap-2">
                    <button
                        onClick={() => setActiveTab('werewolves')}
                        className={`flex-1 py-2 px-4 rounded-lg font-semibold transition-all duration-300 flex items-center justify-center gap-2 ${activeTab === 'werewolves'
                            ? 'bg-red-600 shadow-lg shadow-red-600/50'
                            : 'bg-slate-700 hover:bg-slate-600'
                        }`}
                    >
                        <MessageCircle className="w-4 h-4"/>
                        狼人投票
                    </button>
                    <button
                        onClick={() => setActiveTab('actions')}
                        className={`flex-1 py-2 px-4 rounded-lg font-semibold transition-all duration-300 flex items-center justify-center gap-2 ${activeTab === 'actions'
                            ? 'bg-blue-600 shadow-lg shadow-blue-600/50'
                            : 'bg-slate-700 hover:bg-slate-600'
                        }`}
                    >
                        <Users className="w-4 h-4"/>
                        職業行動
                    </button>
                </div>
            </div>

            <div className="flex-1 overflow-hidden">
                {activeTab === 'werewolves' ? (
                    <WerewolfVotingScreen
                        messages={enrichedMessages}
                        votes={enrichedVotes}
                        messageScrollContainerRef={messageScrollContainerRef}
                        guildId={guildId}
                    />
                ) : (
                    <RoleActionsScreen
                        statuses={enrichedStatuses}
                        wolfTargetName={wolfTargetName}
                        wolfTargetUserId={wolfTargetUserId}
                        wolfTargetId={topTargetId}
                        wolfVoteCount={maxVotes}
                        guildId={guildId}
                    />
                )}
            </div>
        </div>
    );
};

interface WerewolfVotingScreenProps {
    messages: Array<WerewolfMessage & { senderName: string }>;
    votes: Array<WerewolfVote & { voterName: string, targetName: string }>;
    messageScrollContainerRef: React.RefObject<HTMLDivElement>;
    guildId?: string | number | bigint;
}

const WerewolfVotingScreen: React.FC<WerewolfVotingScreenProps> = ({
                                                                       messages,
                                                                       votes,
                                                                       messageScrollContainerRef,
                                                                       guildId
                                                                   }) => {
    return (
        <div className="grid grid-cols-3 gap-4 h-full">
            <div
                className="col-span-2 flex flex-col bg-slate-800/50 rounded-lg overflow-hidden border border-slate-700">
                <div className="px-4 py-2 bg-slate-900 border-b border-slate-700">
                    <h2 className="font-semibold text-sm uppercase tracking-wider">狼人討論</h2>
                </div>
                <div ref={messageScrollContainerRef} className="flex-1 overflow-y-auto space-y-2 p-4 message-scroll">
                    {messages.length === 0 ? (
                        <div className="flex items-center justify-center h-full text-slate-400">等待狼人發言...</div>
                    ) : (
                        (() => {
                            const reversed = [...messages].reverse();
                            return reversed.map((msg, idx) => {
                                const newerMsg = reversed[idx - 1];
                                const olderMsg = reversed[idx + 1];
                                const isContinuation = newerMsg && newerMsg.senderId === msg.senderId && (newerMsg.timestamp - msg.timestamp) < 5 * 60 * 1000;
                                const isPredecessor = olderMsg && olderMsg.senderId === msg.senderId && (msg.timestamp - olderMsg.timestamp) < 5 * 60 * 1000;

                                return (
                                    <div key={`${msg.senderId}-${msg.timestamp}`}
                                         className={`animate-fade-in flex gap-3 px-3 transition-colors group ${isContinuation ? (isPredecessor ? 'py-0 mt-0' : 'pb-2 pt-0 mt-0') : (isPredecessor ? 'pt-2 pb-0 mt-4' : 'py-2 mt-4')} hover:bg-slate-700/30 ${!isContinuation ? 'rounded-t-md' : ''} ${!isPredecessor ? 'rounded-b-md' : ''}`}>
                                        <div className="flex-shrink-0 w-10">
                                            {!isContinuation ? (
                                                <div className="mt-0.5">
                                                    <DiscordAvatar userId={msg.senderUserId} guildId={guildId}
                                                                   avatarClassName="w-10 h-10 rounded-full shadow-sm shadow-black/20"
                                                                   useInitialsFallback={true}
                                                                   avatarFallbackClassName="w-10 h-10 rounded-full bg-slate-600 flex items-center justify-center"
                                                                   avatarFallbackTextClassName="text-xs font-bold text-slate-300"/>
                                                </div>
                                            ) : (
                                                <div
                                                    className="w-10 h-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity min-h-[1.5rem]">
                                                    <span className="text-[10px] text-slate-500 font-mono">•</span>
                                                </div>
                                            )}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            {!isContinuation && (
                                                <div className="flex items-baseline gap-2 mb-0.5">
                                                    <span className="font-semibold text-red-400 text-[15px]">
                                                        <DiscordName userId={msg.senderUserId} guildId={guildId}
                                                                     fallbackName={msg.senderName}/>
                                                    </span>
                                                    <span
                                                        className="text-[10px] text-slate-500 font-medium">{new Date(msg.timestamp).toLocaleTimeString('zh-TW', {
                                                        hour: '2-digit',
                                                        minute: '2-digit'
                                                    })}</span>
                                                </div>
                                            )}
                                            <div
                                                className="text-[15px] text-slate-200 leading-normal break-words">{msg.content}</div>
                                        </div>
                                    </div>
                                );
                            });
                        })()
                    )}
                </div>
            </div>

            <div className="flex flex-col bg-slate-800/50 rounded-lg overflow-hidden border border-slate-700">
                <div className="px-4 py-2 bg-slate-900 border-b border-slate-700">
                    <h2 className="font-semibold text-sm uppercase tracking-wider">投票結果</h2>
                </div>
                <div className="flex-1 overflow-y-auto space-y-2 p-3">
                    {votes.length === 0 ? (
                        <div
                            className="flex items-center justify-center h-full text-slate-400 text-sm">投票進行中...</div>
                    ) : (
                        votes.map((vote, idx) => (
                            <div key={idx}
                                 className="animate-slide-in bg-slate-700/50 rounded px-2 py-2 hover:bg-slate-700 transition-colors text-xs"
                                 style={{animationDelay: `${idx * 50}ms`}}>
                                <div className="font-semibold text-cyan-400 mb-1">
                                    <DiscordName userId={vote.voterUserId} guildId={guildId}
                                                 fallbackName={vote.voterName}/>
                                </div>
                                <div className="flex items-center gap-1">
                                    <ChevronRight className="w-3 h-3 text-amber-400"/>
                                    <span
                                        className={vote.targetName === '跳過' ? 'text-slate-400' : 'text-red-400 font-semibold'}>
                                        <DiscordName userId={vote.targetUserId} guildId={guildId}
                                                     fallbackName={vote.targetName || '未投票'}/>
                                    </span>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>
        </div>
    );
};
