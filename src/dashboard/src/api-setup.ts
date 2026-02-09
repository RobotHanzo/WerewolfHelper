import JSONbig from 'json-bigint';
import {client} from './api/client.gen';

export const JSONbigNative = JSONbig({useNativeBigInt: true});

// Configure the API client with a relative baseURL to ensure requests go through the Vite proxy
client.setConfig({
    baseURL: '',
    withCredentials: true,
    transformResponse: (data) => {
        if (typeof data === 'string') {
            try {
                const a = JSONbigNative.parse(data);
                console.log(a)
                return a;
            } catch (e) {
                return data;
            }
        }
        return data;
    }
});
