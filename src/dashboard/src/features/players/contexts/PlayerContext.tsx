import React, { createContext, ReactNode, useContext, useState } from 'react';
import { getUser } from '@/api/sdk.gen';

interface PlayerContextType {
  userInfoCache: Record<string, { name: string; avatar: string | null }>;
  fetchUserInfo: (
    userId: string,
    guildId: string,
    force?: boolean
  ) => Promise<{
    name: string;
    avatar: string | null;
  } | null>;
  updateSinglePlayerCache: (userId: string, data: { name: string; avatar: string | null }) => void;
  invalidateCache: () => void;
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

export const PlayerProvider: React.FC<PlayerProviderProps> = ({ children }) => {
  const [userInfoCache, setUserInfoCache] = useState<
    Record<string, { name: string; avatar: string | null }>
  >({});
  const pendingRequests = React.useRef<
    Record<
      string,
      | Promise<{
          name: string;
          avatar: string | null;
        } | null>
      | undefined
    >
  >({});

  const invalidateCache = () => {
    setUserInfoCache({});
  };

  const updateSinglePlayerCache = (
    userId: string,
    data: { name: string; avatar: string | null }
  ) => {
    setUserInfoCache((prev) => {
      const current = prev[userId];
      if (!current || current.name !== data.name || current.avatar !== data.avatar) {
        return {
          ...prev,
          [userId]: data,
        };
      }
      return prev;
    });
  };

  const fetchUserInfo = async (userId: string, guildId: string, force: boolean = false) => {
    if (!force && userInfoCache[userId]) return userInfoCache[userId];
    if (pendingRequests.current[userId]) return pendingRequests.current[userId];

    const promise = (async () => {
      try {
        const response = await getUser({
          path: { guildId: guildId, userId: userId },
        });
        const data = response.data;
        if (!data || !data.success || !data.data) return null;

        const userData = data.data;
        const info = {
          name: String(userData.name),
          avatar: userData.avatar ? String(userData.avatar) : null,
        };
        setUserInfoCache((prev) => ({
          ...prev,
          [userId]: info,
        }));
        delete pendingRequests.current[userId];
        return info;
      } catch (e) {
        console.error(`Failed to fetch user info for ${userId}`, e);
        delete pendingRequests.current[userId];
        return null;
      }
    })();

    pendingRequests.current[userId] = promise;
    return promise;
  };

  return (
    <PlayerContext.Provider
      value={{ userInfoCache, fetchUserInfo, updateSinglePlayerCache, invalidateCache }}
    >
      {children}
    </PlayerContext.Provider>
  );
};
