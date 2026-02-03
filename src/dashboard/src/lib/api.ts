export class ApiClient {
    private baseUrl: string;

    constructor() {
        this.baseUrl = this.getBackendUrl();
        console.log('ApiClient initialized with baseUrl:', this.baseUrl);
        if (this.baseUrl !== '') {
            console.warn('Backend URL is not empty! Forcing reset.');
            this.baseUrl = '';
        }
    }

    private getBackendUrl(): string {
        // return localStorage.getItem('backendUrl') || DEFAULT_BACKEND_URL;
        return ''; // Force local proxy
    }

    public setBackendUrl(url: string) {
        localStorage.setItem('backendUrl', url);
        this.baseUrl = url;
    }

    public getConfiguredUrl(): string {
        return this.baseUrl;
    }

    private async request<T>(endpoint: string, options?: RequestInit): Promise<T> {
        const url = `${this.baseUrl}${endpoint}`;

        try {
            const response = await fetch(url, {
                credentials: 'include', // Ensure cookies are sent
                ...options,
                headers: {
                    'Content-Type': 'application/json',
                    ...options?.headers,
                },
            });

            let data;
            try {
                data = await response.json();
            } catch (e) {
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                throw new Error('Failed to parse response');
            }

            if (!response.ok) {
                throw new Error(data.error || data.message || `HTTP error! status: ${response.status}`);
            }

            if (data.success === false) {
                throw new Error(data.error || 'API request failed');
            }

            return data.data || data;
        } catch (error) {
            console.error(`API request failed: ${endpoint}`, error);
            throw error;
        }
    }

    // Session endpoints
    async getSessions() {
        return this.request('/api/sessions');
    }

    async getSession(guildId: string) {
        return this.request(`/api/sessions/${guildId}`);
    }

    // Player endpoints
    async getPlayers(guildId: string) {
        return this.request(`/api/sessions/${guildId}/players`);
    }

    async assignRoles(guildId: string) {
        return this.request(`/api/sessions/${guildId}/players/assign`, {method: 'POST'});
    }

    async markPlayerDead(guildId: string, userId: string, lastWords: boolean = false) {
        return this.request(`/api/sessions/${guildId}/players/${userId}/died?lastWords=${lastWords}`, {method: 'POST'});
    }

    async setPolice(guildId: string, userId: string) {
        return this.request(`/api/sessions/${guildId}/players/${userId}/police`, {method: 'POST'});
    }

    async updatePlayerRoles(guildId: string, userId: string, roles: string[]) {
        return this.request(`/api/sessions/${guildId}/players/${userId}/roles`, {
            method: 'POST',
            body: JSON.stringify(roles)
        });
    }

    async reviveRole(guildId: string, userId: string, role: string) {
        return this.request(`/api/sessions/${guildId}/players/${userId}/revive-role?role=${encodeURIComponent(role)}`, {method: 'POST'});
    }

    async revivePlayer(guildId: string, userId: string) {
        return this.request(`/api/sessions/${guildId}/players/${userId}/revive`, {method: 'POST'});
    }

    // Role management
    async getRoles(guildId: string) {
        return this.request(`/api/sessions/${guildId}/roles`);
    }

    async addRole(guildId: string, role: string, amount: number = 1) {
        return this.request(`/api/sessions/${guildId}/roles/add?role=${encodeURIComponent(role)}&amount=${amount}`, {method: 'POST'});
    }

    async removeRole(guildId: string, role: string, amount: number = 1) {
        return this.request(`/api/sessions/${guildId}/roles/${encodeURIComponent(role)}?amount=${amount}`, {method: 'DELETE'});
    }

    // Role Order
    async switchRoleOrder(guildId: string, playerId: string) {
        return this.request(`/api/sessions/${guildId}/players/${playerId}/switch-role-order`, {
            method: 'POST'
        });
    }

    // Role Position Lock
    async setPlayerRoleLock(guildId: string, playerId: string, locked: boolean) {
        return this.request(`/api/sessions/${guildId}/players/${playerId}/role-lock?locked=${locked}`, {
            method: 'POST'
        });
    }

    // Settings
    async updateSettings(guildId: string, settings: { doubleIdentities?: boolean; muteAfterSpeech?: boolean }) {
        return this.request(`/api/sessions/${guildId}/settings`, {
            method: 'PUT',
            body: JSON.stringify(settings)
        });
    }

    async setPlayerCount(guildId: string, count: number) {
        return this.request(`/api/sessions/${guildId}/player-count`, {
            method: 'POST',
            body: JSON.stringify({count})
        });
    }

    // Speech endpoints
    async startSpeech(guildId: string) {
        return this.request(`/api/sessions/${guildId}/speech/auto`, {method: 'POST'});
    }

    async skipSpeech(guildId: string) {
        return this.request(`/api/sessions/${guildId}/speech/skip`, {method: 'POST'});
    }

    async interruptSpeech(guildId: string) {
        return this.request(`/api/sessions/${guildId}/speech/interrupt`, {method: 'POST'});
    }

    async startPoliceEnroll(guildId: string) {
        return this.request(`/api/sessions/${guildId}/speech/police-enroll`, {method: 'POST'});
    }

    async setSpeechOrder(guildId: string, direction: 'UP' | 'DOWN') {
        return this.request(`/api/sessions/${guildId}/speech/order`, {
            method: 'POST',
            body: JSON.stringify({direction})
        });
    }

    async confirmSpeech(guildId: string) {
        return this.request(`/api/sessions/${guildId}/speech/confirm`, {method: 'POST'});
    }

    // Start and Reset session
    async startGame(guildId: string) {
        return this.request(`/api/sessions/${guildId}/start`, {method: 'POST'});
    }

    async resetSession(guildId: string) {
        return this.request(`/api/sessions/${guildId}/reset`, {method: 'POST'});
    }

    // Game State Machine
    async getGameState(guildId: string) {
        return this.request(`/api/sessions/${guildId}/state`);
    }

    async nextState(guildId: string) {
        return this.request(`/api/sessions/${guildId}/state/next`, {method: 'POST'});
    }

    async setState(guildId: string, stepId: string) {
        return this.request(`/api/sessions/${guildId}/state/set`, {
            method: 'POST',
            body: JSON.stringify({stepId})
        });
    }

    async stateAction(guildId: string, action: any) {
        return this.request(`/api/sessions/${guildId}/state/action`, {
            method: 'POST',
            body: JSON.stringify(action)
        });
    }

    // New Commands (Timer, Voice, Roles)
    async manualStartTimer(guildId: string, duration: number) {
        return this.request(`/api/sessions/${guildId}/speech/manual-start`, {
            method: 'POST',
            body: JSON.stringify({duration})
        });
    }

    async muteAll(guildId: string) {
        return this.request(`/api/sessions/${guildId}/speech/mute-all`, {method: 'POST'});
    }

    async unmuteAll(guildId: string) {
        return this.request(`/api/sessions/${guildId}/speech/unmute-all`, {method: 'POST'});
    }

    async updateUserRole(guildId: string, userId: string, role: string) {
        return this.request(`/api/sessions/${guildId}/players/${userId}/role`, {
            method: 'POST',
            body: JSON.stringify({role})
        });
    }

    async getGuildMembers(guildId: string): Promise<any[]> {
        return this.request(`/api/sessions/${guildId}/members`);
    }

    // Test connection
    async testConnection(): Promise<boolean> {
        try {
            await this.getSessions();
            return true;
        } catch {
            return false;
        }
    }
}

export const api = new ApiClient();
