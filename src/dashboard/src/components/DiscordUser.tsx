import React, {useEffect, useMemo, useState} from 'react';
import {usePlayerContext} from '@/features/players/contexts/PlayerContext';
import {useAuth} from '@/features/auth/contexts/AuthContext';

export interface DiscordUserBaseProps {
    userId?: string | number | bigint | null;
    guildId?: string | number | bigint | null;
}

export interface DiscordNameBaseProps extends DiscordUserBaseProps {
    fallbackName?: string;
}

export interface DiscordAvatarProps extends DiscordUserBaseProps {
    avatarClassName?: string;
    loadingAvatarClassName?: string;
    avatarFallbackClassName?: string;
    avatarFallbackTextClassName?: string;
    useInitialsFallback?: boolean;
}

export interface DiscordNameProps extends DiscordNameBaseProps {
    nameClassName?: string;
}

const resolveAvatarUrl = (userId?: string | number | bigint | null, avatar?: string | null) => {
    if (!avatar) return null;
    if (avatar.startsWith('http')) return avatar;
    if (!userId) return null;
    return `https://cdn.discordapp.com/avatars/${String(userId)}/${avatar}.png?size=128`;
};

const useDiscordUserData = ({userId: rawUserId, guildId: rawGuildId, fallbackName}: DiscordNameBaseProps) => {
    const {user} = useAuth();
    const {userInfoCache, fetchUserInfo} = usePlayerContext();
    const [isLoading, setIsLoading] = useState(false);

    const userId = rawUserId ? String(rawUserId) : null;
    const guildId = rawGuildId ? String(rawGuildId) : (user?.user?.guildId ? String(user.user.guildId) : null);
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
        const idStr = userId ? String(userId) : '';
        return cachedUser?.name || fallbackName || (idStr ? `玩家 ${idStr}` : 'Unknown');
    }, [cachedUser?.name, fallbackName, userId]);

    const avatarUrl = useMemo(() => {
        return resolveAvatarUrl(userId, cachedUser?.avatar || null);
    }, [userId, cachedUser?.avatar]);

    return {name, avatarUrl, isLoading, cachedUser};
};

export const DiscordAvatar: React.FC<DiscordAvatarProps> = ({
                                                                userId,
                                                                guildId,
                                                                avatarClassName,
                                                                loadingAvatarClassName,
                                                                avatarFallbackClassName,
                                                                avatarFallbackTextClassName,
                                                                useInitialsFallback = false
                                                            }) => {
    const {name, avatarUrl, cachedUser} = useDiscordUserData({userId, guildId});

    const shouldUseInitialsFallback =
        useInitialsFallback && !userId && !cachedUser?.avatar;

    const loadingElement = (
        <div
            className={`${avatarClassName || ''} ${loadingAvatarClassName || 'animate-pulse bg-slate-300/70 dark:bg-slate-700/70'}`.trim()}
        />
    );

    if (!avatarUrl) {
        if (shouldUseInitialsFallback) {
            return (
                <div className={avatarFallbackClassName || avatarClassName}>
                    <span className={avatarFallbackTextClassName}>{name.substring(0, 1)}</span>
                </div>
            );
        }
        return loadingElement;
    }

    return (
        <img
            src={avatarUrl}
            alt={name}
            className={avatarClassName}
        />
    );
};

export const DiscordName: React.FC<DiscordNameProps> = ({userId, guildId, fallbackName, nameClassName}) => {
    const {name} = useDiscordUserData({userId, guildId, fallbackName});
    return <span className={nameClassName}>{name}</span>;
};
