import React, {useEffect, useState} from 'react';
import {Clock} from 'lucide-react';
import {Player} from '@/types';
import {useTranslation} from '@/lib/i18n';

interface VoteStatusProps {
    candidates: { id: string, voters: string[] }[];
    totalVoters?: number;
    endTime?: number;
    players: Player[];
    title?: string;
    onTimerExpired?: () => void;
}

export const VoteStatus: React.FC<VoteStatusProps> = ({
                                                          candidates,
                                                          totalVoters,
                                                          endTime,
                                                          players,
                                                          title,
                                                          onTimerExpired
                                                      }) => {
    const {t} = useTranslation();
    const [timeLeft, setTimeLeft] = useState(0);

    useEffect(() => {
        if (!endTime) {
            setTimeLeft(0);
            return;
        }
        const interval = setInterval(() => {
            const remaining = Math.max(0, Math.ceil((endTime - Date.now()) / 1000));
            setTimeLeft(remaining);

            // Trigger callback when timer expires
            if (remaining === 0 && onTimerExpired) {
                onTimerExpired();
            }
        }, 100);
        return () => clearInterval(interval);
    }, [endTime, onTimerExpired]);

    // Calculate total votes cast
    const totalVotes = candidates.reduce((acc, c) => acc + c.voters.length, 0);
    const progress = totalVoters ? (totalVotes / totalVoters) * 100 : 0;

    return (
        <div className="flex flex-col gap-6 w-full">
            {/* Timer and Header */}
            <div className="flex items-center justify-between">
                <h3 className="text-lg font-bold text-slate-700 dark:text-slate-200">{title || t('vote.progress')}</h3>
                <div className={`flex items-center gap-2 text-xl font-mono font-bold px-4 py-2 rounded-lg border 
                    ${timeLeft < 10
                    ? 'text-red-600 bg-red-50 border-red-200 dark:text-red-400 dark:bg-red-900/20 dark:border-red-900/50'
                    : 'text-slate-700 bg-slate-100 border-slate-200 dark:text-slate-300 dark:bg-slate-800 dark:border-slate-700'
                }`}>
                    <Clock className="w-5 h-5"/>
                    <span>
                        {Math.floor(timeLeft / 60).toString().padStart(2, '0')}:{String(timeLeft % 60).padStart(2, '0')}
                    </span>
                </div>
            </div>

            {/* Progress Bar */}
            {totalVoters && (
                <div className="space-y-1">
                    <div className="flex justify-between text-xs text-slate-500 font-medium uppercase tracking-wider">
                        <span>{t('vote.total')}</span>
                        <span>{totalVotes} / {totalVoters}</span>
                    </div>
                    <div className="w-full bg-slate-200 dark:bg-slate-700 rounded-full h-2 overflow-hidden">
                        <div
                            className="bg-indigo-500 h-2 rounded-full transition-all duration-300 ease-out"
                            style={{width: `${Math.min(100, progress)}%`}}
                        />
                    </div>
                </div>
            )}

            {/* Candidates Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {candidates.map((candidate) => {
                    const player = players.find(p => p.id === candidate.id);
                    return (
                        <div key={candidate.id}
                             className="bg-slate-50 dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 rounded-xl p-4 flex flex-col gap-3">
                            {/* Candidate Info */}
                            <div
                                className="flex items-center gap-3 pb-3 border-b border-slate-200 dark:border-slate-800">
                                <img
                                    src={player?.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png'}
                                    className="w-10 h-10 rounded-full shadow-sm"
                                    alt={player?.name}
                                />
                                <div className="flex-1">
                                    <h4 className="font-bold text-slate-800 dark:text-slate-200">{player?.name || `Candidate ${candidate.id}`}</h4>
                                    <span
                                        className="text-xs font-bold text-indigo-500 bg-indigo-50 dark:bg-indigo-900/30 px-2 py-0.5 rounded-full border border-indigo-100 dark:border-indigo-900/50">
                                        {candidate.voters.length} {t('vote.count')}
                                    </span>
                                </div>
                            </div>

                            {/* Voters List */}
                            <div className="flex flex-wrap gap-2 min-h-[32px]">
                                {candidate.voters.length > 0 ? (
                                    candidate.voters.map(voterId => {
                                        // Try to find voter by userId (assuming voterId is userId string) 
                                        // The backend sends userId as string for voters. 
                                        // Player.userId is key.
                                        const voter = players.find(p => p.userId === voterId);
                                        return (
                                            <div key={voterId}
                                                 className="flex items-center gap-1.5 bg-white dark:bg-slate-800 px-2 py-1 rounded-md text-xs border border-slate-200 dark:border-slate-700 shadow-sm animate-in zoom-in-50">
                                                <img
                                                    src={voter?.avatar || 'https://cdn.discordapp.com/embed/avatars/0.png'}
                                                    className="w-4 h-4 rounded-full" alt=""/>
                                                <span
                                                    className="font-medium text-slate-600 dark:text-slate-400 max-w-[80px] truncate">
                                                    {voter?.name || 'Unknown'}
                                                </span>
                                            </div>
                                        );
                                    })
                                ) : (
                                    <span className="text-xs text-slate-400 italic">{t('vote.noVotes')}</span>
                                )}
                            </div>
                        </div>
                    );
                })}
            </div>

            {/* Not Voted List (Optional, maybe for future) */}
            {totalVoters && (totalVotes < totalVoters) && (
                <div className="text-xs text-slate-400 text-center">
                    {t('vote.waiting', {count: String(totalVoters - totalVotes)})}
                </div>
            )}
        </div>
    );
};
