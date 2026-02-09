import {defineConfig} from '@hey-api/openapi-ts';

export default defineConfig({
    input: 'http://localhost:8080/v3/api-docs',
    output: 'src/api',
    plugins: [
        '@hey-api/typescript',
        '@hey-api/client-axios',
        {
            name: '@hey-api/sdk',
        },
        '@tanstack/react-query',
    ],
});