import {useEffect, useRef} from 'react';
import {useNavigate} from 'react-router-dom';
import {Player, AuthData as User} from '@/api/types.gen';
import {selectGuild} from '@/api/sdk.gen';

export const useDashboardAuth = (
    guildId: string | undefined,
    user: User | null,
    loading: boolean,
    checkAuth: () => Promise<void>,
    gameStatePlayers: Player[]
) => {
    const navigate = useNavigate();
    const isSelectingGuild = useRef(false);

    // Check authentication and authorization
    useEffect(() => {
        if (loading) return;

        if (!user) {
            navigate('/login');
            return;
        }

        // PENDING users haven't selected a server yet, skip initial check
        if (user.user.role === 'PENDING') {
            // If they're trying to access a specific server, update their role
            if (guildId && !isSelectingGuild.current) {
                isSelectingGuild.current = true;
                const selectGuildAsync = async () => {
                    try {
                        const response = await selectGuild({
                            path: {guildId}
                        });

                        if (response.data?.success) {
                            // Refresh auth state to get updated role
                            await checkAuth();
                        }
                    } catch (error) {
                        console.error('Failed to select guild:', error);
                    } finally {
                        isSelectingGuild.current = false;
                    }
                };
                selectGuildAsync();
            }
            return;
        }

        // For non-PENDING users, check if they're accessing a different guild
        const currentGuildId = user.user.guildId;
        if (guildId && currentGuildId && currentGuildId !== guildId && !isSelectingGuild.current) {
            // Allow switching guilds by calling select-guild
            isSelectingGuild.current = true;
            const switchGuild = async () => {
                try {
                    const response = await selectGuild({
                        path: {guildId}
                    });

                    if (response.data?.success) {
                        // Refresh auth state to get updated role for new guild
                        await checkAuth();
                    }
                } catch (error) {
                    console.error('Failed to switch guild:', error);
                    alert('Failed to switch server. Please try again.');
                } finally {
                    isSelectingGuild.current = false;
                }
            };
            switchGuild();
            return;
        }

        // After ensuring we are on the correct guild, check if the user is BLOCKED
        if (user.user.role === 'BLOCKED') {
            if (!window.location.pathname.includes('/access-denied')) {
                navigate('/access-denied');
            }
            return;
        }

        // Role-based route redirection for already authorized users
        if (user.user.role === 'SPECTATOR') {
            const path = window.location.pathname;
            const baseUrl = `/server/${guildId}`;
            if (path === baseUrl || path === `${baseUrl}/` || path.includes('/settings')) {
                navigate(`${baseUrl}/spectator`);
            }
        }
    }, [user, loading, guildId, navigate, checkAuth]);

    // Security check: Lock dashboard if player has assigned roles and is not a privileged user
    useEffect(() => {
        if (!user || loading) return;

        // Privileged roles are exempt
        if (user.user.role === 'JUDGE' || user.user.role === 'SPECTATOR') return;

        // Check if current user has any in-game roles
        const currentPlayer = gameStatePlayers.find(p => p.userId === user.user.userId);

        // If player exists and has roles assigned, lock them out
        if (currentPlayer && currentPlayer.roles && currentPlayer.roles.length > 0) {
            navigate('/access-denied');
        }
    }, [user, loading, gameStatePlayers, navigate]);

    // Return if the guild is ready (user matches guild)
    return !!(user && user.user.guildId && user.user.guildId === guildId);
};
