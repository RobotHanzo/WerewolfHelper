# Werewolf Helper Dashboard

This is the admin dashboard frontend for the Werewolf Discord Bot. It allows admins to view the game state in real-time
and execute commands.
*Made by vibe-coding using Google Antigravity, I take no credit for the design, and no responsibility for any issues
that may arise.*

## Prerequisites

- [Node.js](https://nodejs.org/) (Version 16 or higher)
- [Yarn](https://yarnpkg.com/)

## Installation

1. Clone the repository (or download the source).
2. Install dependencies:

   ```bash
   yarn install
   ```

## Development

To start the local development server:

```bash
yarn dev
```

The application will be available at `http://localhost:5173`.

## Production Build

To build the application for production:

```bash
yarn build
```

The output will be in the `dist/` directory. You can serve this static directory using any web server (Nginx, Apache,
Vercel, Netlify, etc.).

### Preview Production Build

To preview the production build locally:

```bash
yarn preview
```

## Discord OAuth Configuration

The dashboard uses Discord OAuth2 for authentication. To set this up:

1. **Create a Discord Application**:
    - Go to the [Discord Developer Portal](https://discord.com/developers/applications)
    - Click **New Application** and give it a name
    - Navigate to the **OAuth2** section

2. **Configure Redirect URIs**:
    - Add your redirect URI (e.g., `http://localhost:5173/auth/callback` for local development)
    - For production, use your deployed dashboard URL (e.g., `https://yourdomain.com/auth/callback`)

3. **Get Your Credentials**:
    - Copy your **Client ID** from the General Information page
    - Generate a **Client Secret** from the OAuth2 page

4. **Set Environment Variables**:
    - The backend requires the following environment variables:
      ```bash
      DISCORD_CLIENT_ID=your_client_id_here
      DISCORD_CLIENT_SECRET=your_client_secret_here
      DISCORD_REDIRECT_URI=http://localhost:5173/auth/callback
      DASHBOARD_URL=http://localhost:5173
      ```

5. **Bot Permissions**:
    - The OAuth2 application needs the following scopes: `identify`, `guilds`, `guilds.members.read`

## Integration

Refer to the "Integration Guide" within the dashboard application for details on how to connect this frontend to your
Java Discord Bot backend.
