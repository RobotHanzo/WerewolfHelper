import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

interface User {
    userId: string;
    username: string;
    avatar: string;
    guildId: number;
    role: 'JUDGE' | 'SPECTATOR' | 'BLOCKED' | 'PENDING';
}

interface AuthContextType {
    user: User | null;
    loading: boolean;
    login: (guildId: string) => void;
    logout: () => Promise<void>;
    checkAuth: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within AuthProvider');
    }
    return context;
};

interface AuthProviderProps {
    children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
    const [user, setUser] = useState<User | null>(null);
    const [loading, setLoading] = useState(true);

    const checkAuth = async () => {
        try {
            const response = await fetch('/api/auth/me', {
                credentials: 'include'
            });

            // Check if the response is JSON
            const contentType = response.headers.get('content-type');
            if (!contentType || !contentType.includes('application/json')) {
                // Backend is not running or returned HTML
                console.warn('Backend not responding with JSON, user not authenticated');
                setUser(null);
                return;
            }

            if (response.ok) {
                const data = await response.json();
                if (data.success) {
                    setUser(data.user);
                } else {
                    setUser(null);
                }
            } else {
                setUser(null);
            }
        } catch (error) {
            console.error('Auth check failed:', error);
            setUser(null);
        } finally {
            setLoading(false);
        }
    };

    const login = (guildId: string) => {
        window.location.href = `/api/auth/login?guild_id=${guildId}`;
    };

    const logout = async () => {
        try {
            await fetch('/api/auth/logout', {
                method: 'POST',
                credentials: 'include'
            });
            setUser(null);
        } catch (error) {
            console.error('Logout failed:', error);
        }
    };

    useEffect(() => {
        checkAuth();
    }, []);

    return (
        <AuthContext.Provider value={{ user, loading, login, logout, checkAuth }}>
            {children}
        </AuthContext.Provider>
    );
};
