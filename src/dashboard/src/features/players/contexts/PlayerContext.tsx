import React, {createContext, ReactNode, useContext, useState} from 'react';
import {api} from '@/lib/api';

interface PlayerContextType {
    userInfoCache: Record<string, { name: string, avatar: string }>;
    fetchUserInfo: (userId: string, guildId: string) => Promise<{ name: string, avatar: string } | null>;
}

const PlayerContext = createContext<PlayerContextType | undefined>(undefined);

export const usePlayerContext = () => {
    const context = useContext(PlayerContext);
    if (!context) {
        throw new Error('usePlayerContext must be used within PlayerProvider');
    }
    return context;
};

interface PlayerProviderProps {
    children: ReactNode;
}

export const PlayerProvider: React.FC<PlayerProviderProps> = ({children}) => {
    const [userInfoCache, setUserInfoCache] = useState<Record<string, { name: string, avatar: string }>>({});

    const fetchUserInfo = async (userId: string, guildId: string) => {
        if (userInfoCache[userId]) return userInfoCache[userId];

        try {
            const data: any = await api.getUserInfo(guildId, userId);
            const info = {name: data.name, avatar: data.avatar};
            setUserInfoCache(prev => ({
                ...prev,
                [userId]: info
            }));
            return info;
        } catch (e) {
            console.error(`Failed to fetch user info for ${userId}`, e);
            return null;
        }
    };

    return (
        <PlayerContext.Provider value={{userInfoCache, fetchUserInfo}}>
            {children}
        </PlayerContext.Provider>
    );
};
