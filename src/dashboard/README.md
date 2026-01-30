# Werewolf Helper Dashboard

This is the admin dashboard frontend for the Werewolf Discord Bot. It allows admins to view the game state in real-time and execute commands.

## Prerequisites

- [Node.js](https://nodejs.org/) (Version 16 or higher)
- [Yarn](https://yarnpkg.com/)

## Installation

1.  Clone the repository (or download the source).
2.  Install dependencies:

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

The output will be in the `dist/` directory. You can serve this static directory using any web server (Nginx, Apache, Vercel, Netlify, etc.).

### Preview Production Build

To preview the production build locally:

```bash
yarn preview
```

## Integration

Refer to the "Integration Guide" within the dashboard application for details on how to connect this frontend to your Java Discord Bot backend.
