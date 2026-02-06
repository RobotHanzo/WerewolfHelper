import React, {useEffect, useMemo, useState} from 'react';
import {usePlayerContext} from '@/features/players/contexts/PlayerContext';
import {useAuth} from '@/features/auth/contexts/AuthContext';

export interface DiscordUserRenderProps {
    name: string;
    isLoading: boolean;
    avatarElement: React.ReactNode;
}

export interface DiscordUserProps {
    userId?: string | null;
    guildId?: string;
    fallbackName?: string;
    showAvatar?: boolean;
    showName?: boolean;
    avatarClassName?: string;
    loadingAvatarClassName?: string;
    avatarFallbackClassName?: string;
    avatarFallbackTextClassName?: string;
    useInitialsFallback?: boolean;
    nameClassName?: string;
    className?: string;
    children?: (data: DiscordUserRenderProps) => React.ReactNode;
}

const resolveAvatarUrl = (userId?: string | null, avatar?: string | null) => {
    if (!avatar) return null;
    if (avatar.startsWith('http')) return avatar;
    if (!userId) return null;
    return `https://cdn.discordapp.com/avatars/${userId}/${avatar}.png?size=128`;
};

export const DiscordUser: React.FC<DiscordUserProps> = ({
                                                            userId,
                                                            guildId: propGuildId,
                                                            fallbackName,
                                                            showAvatar = true,
                                                            showName = false,
                                                            avatarClassName,
                                                            loadingAvatarClassName,
                                                            avatarFallbackClassName,
                                                            avatarFallbackTextClassName,
                                                            useInitialsFallback = false,
                                                            nameClassName,
                                                            className,
                                                            children
                                                        }) => {
    const {user} = useAuth();
    const {userInfoCache, fetchUserInfo} = usePlayerContext();
    const [isLoading, setIsLoading] = useState(false);

    const guildId = propGuildId || user?.guildId;
    const cachedUser = userId ? userInfoCache[userId] : null;

    useEffect(() => {
        let mounted = true;
        const run = async () => {
            if (!userId || !guildId || cachedUser) return;
            setIsLoading(true);
            await fetchUserInfo(userId, guildId);
            if (mounted) {
                setIsLoading(false);
            }
        };
        run();
        return () => {
            mounted = false;
        };
    }, [userId, guildId, cachedUser, fetchUserInfo]);

    const name = useMemo(() => {
        return cachedUser?.name || fallbackName || (userId ? `玩家 ${userId}` : 'Unknown');
    }, [cachedUser?.name, fallbackName, userId]);

    const avatarUrl = useMemo(() => {
        return resolveAvatarUrl(userId, cachedUser?.avatar || null);
    }, [userId, cachedUser?.avatar]);

    const shouldUseInitialsFallback =
        useInitialsFallback && !userId && !cachedUser?.avatar;

    const loadingElement = (
        <div
            className={`${avatarClassName || ''} ${loadingAvatarClassName || 'animate-pulse bg-slate-300/70 dark:bg-slate-700/70'}`.trim()}
        />
    );

    const avatarElement = showAvatar
        ? (!avatarUrl
            ? (shouldUseInitialsFallback
                ? (
                    <div className={avatarFallbackClassName || avatarClassName}>
                        <span className={avatarFallbackTextClassName}>{name.substring(0, 1)}</span>
                    </div>
                )
                : loadingElement)
            : (
                <img
                    src={avatarUrl}
                    alt={name}
                    className={avatarClassName}
                />
            ))
        : null;

    if (children) {
        return <>{children({name, isLoading, avatarElement})}</>;
    }

    return (
        <div className={className}>
            {avatarElement}
            {showName && (
                <span className={nameClassName}>{name}</span>
            )}
        </div>
    );
};
