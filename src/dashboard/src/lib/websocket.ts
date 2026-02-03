import {useEffect, useRef, useState} from 'react';
import {api} from './api';

type MessageHandler = (data: any) => void;

export class WebSocketClient {
    private ws: WebSocket | null = null;
    private reconnectTimeout: number | null = null;
    private messageHandlers: Set<MessageHandler> = new Set();
    private url: string;
    private reconnectAttempts = 0;
    private guildId: string | null = null;

    private onConnectHandlers: Set<() => void> = new Set();
    private onDisconnectHandlers: Set<(event: CloseEvent) => void> = new Set();

    constructor() {
        this.url = this.getWebSocketUrl();
    }

    private getWebSocketUrl(): string {
        const backendUrl = api.getConfiguredUrl();
        const query = this.guildId ? `?guildId=${this.guildId}` : '';
        if (!backendUrl) {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const host = window.location.host;
            return `${protocol}//${host}/ws${query}`;
        }
        return (backendUrl.replace(/^http/, 'ws') + '/ws').replace(/\/+$/, '') + query;
    }

    public get isConnected(): boolean {
        return this.ws?.readyState === WebSocket.OPEN;
    }

    connect(guildId?: string) {
        if (guildId !== undefined) {
            if (this.guildId !== guildId) {
                this.guildId = guildId;
                if (this.ws) {
                    this.disconnect();
                }
            }
        }

        if (this.ws?.readyState === WebSocket.OPEN || this.ws?.readyState === WebSocket.CONNECTING) {
            return;
        }

        this.url = this.getWebSocketUrl();
        console.log(`Connecting to WebSocket at ${this.url}`);

        try {
            this.ws = new WebSocket(this.url);

            this.ws.onopen = () => {
                console.log('WebSocket connected');
                this.reconnectAttempts = 0;
                this.onConnectHandlers.forEach(h => h());
                if (this.reconnectTimeout) {
                    clearTimeout(this.reconnectTimeout);
                    this.reconnectTimeout = null;
                }
            };

            this.ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    if (data.type === 'PONG') return;
                    this.messageHandlers.forEach(handler => handler(data));
                } catch (error) {
                    // Ignore parsing errors for heartbeats
                }
            };

            this.ws.onerror = (error) => {
                console.error('WebSocket error:', error);
                // onerror is usually followed by onclose
            };

            this.ws.onclose = (event) => {
                console.log(`WebSocket closed (code: ${event.code}, reason: ${event.reason}), reconnecting...`);
                this.ws = null;
                this.onDisconnectHandlers.forEach(h => h(event));

                // Check if this is a session-related rejection (1000 is normal close, 1008 is policy violation)
                const isSessionRejection = event.code === 1008 || event.code === 1000 || event.code === 1001;
                const isSessionError = event.reason && (
                    event.reason.includes("No user in session") ||
                    event.reason.includes("Rejected WS connection") ||
                    event.reason.includes("expired") ||
                    event.reason.includes("Session")
                );

                if (isSessionRejection && isSessionError) {
                    // Don't reconnect for session rejections, let the handlers deal with it
                    return;
                }
                
                this.reconnect();
            };
        } catch (error) {
            console.error('Failed to create WebSocket:', error);
            this.reconnect();
        }
    }

    private reconnect() {
        if (this.reconnectTimeout) return;

        // Exponential backoff: 1s, 2s, 5s, max 10s
        const delay = Math.min(1000 * Math.pow(1.5, this.reconnectAttempts), 10000);
        this.reconnectAttempts++;

        this.reconnectTimeout = window.setTimeout(() => {
            this.reconnectTimeout = null;
            console.log(`Attempting to reconnect WebSocket (attempt ${this.reconnectAttempts})...`);
            this.connect();
        }, delay);
    }

    disconnect() {
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }

        if (this.ws) {
            this.ws.onclose = null; // Prevent auto-reconnect on manual disconnect
            const closeEvent = new CloseEvent('close', {code: 1000, reason: 'Manual disconnect'});
            this.ws.close();
            this.ws = null;
            this.onDisconnectHandlers.forEach(h => h(closeEvent));
        }
    }

    addConnectionHandlers(onConnect: () => void, onDisconnect: (event: CloseEvent) => void) {
        this.onConnectHandlers.add(onConnect);
        this.onDisconnectHandlers.add(onDisconnect);

        // Return cleanup function
        return () => {
            this.onConnectHandlers.delete(onConnect);
            this.onDisconnectHandlers.delete(onDisconnect);
        };
    }

    onMessage(handler: MessageHandler) {
        this.messageHandlers.add(handler);
        return () => this.messageHandlers.delete(handler);
    }

    send(data: any) {
        if (this.ws?.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(data));
        } else {
            console.warn('Cannot send message: WebSocket is not open');
        }
    }
}

// Global singleton instance
export const wsClient = new WebSocketClient();

// React hook for WebSocket using the singleton
export function useWebSocket(onMessage: MessageHandler, guildId?: string, onSessionExpired?: () => void) {
    const [isConnected, setIsConnected] = useState(wsClient.isConnected);
    const onMessageRef = useRef(onMessage);

    useEffect(() => {
        onMessageRef.current = onMessage;
    }, [onMessage]);

    useEffect(() => {
        // Subscribe to connection changes
        const unsubscribeConn = wsClient.addConnectionHandlers(
            () => setIsConnected(true),
            (event) => {
                setIsConnected(false);

                // Check for session expiration or rejection
                const isSessionRejection = event.code === 1008 || event.code === 1000 || event.code === 1001;
                const isSessionError = event.reason && (
                    event.reason.includes("No user in session") ||
                    event.reason.includes("Rejected WS connection") ||
                    event.reason.includes("expired") ||
                    event.reason.includes("Session")
                );

                if (isSessionRejection && isSessionError) {
                    console.warn('Session expired or rejected:', event.reason);
                    if (onSessionExpired) {
                        onSessionExpired();
                    } else {
                        // Fallback if no handler provided (e.g. login page)
                        console.warn("Session expired handling not implemented in this context");
                    }
                }
            }
        );

        // Subscribe to messages
        const unsubscribeMsg = wsClient.onMessage((data) => {
            onMessageRef.current(data);
        });

        // Ensure we are connected
        wsClient.connect(guildId);

        // Heartbeat interval
        const interval = setInterval(() => {
            if (wsClient.isConnected) {
                wsClient.send({type: 'PING'});
            } else {
                wsClient.connect(guildId); // Force check if somehow stuck
            }
        }, 15000);

        return () => {
            unsubscribeConn();
            unsubscribeMsg();
            clearInterval(interval);
        };
    }, []);

    return {isConnected, ws: wsClient};
}
