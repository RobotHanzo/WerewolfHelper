import zhTW from '../locales/zh-TW.json';

type TranslationKey = string;
type Translations = typeof zhTW;

// Simple i18n without external dependencies
const translations: Translations = zhTW;

export const useTranslation = () => {
    const t = (key: TranslationKey, params?: Record<string, string>): string => {
        const keys = key.split('.');
        let value: any = translations;

        for (const k of keys) {
            if (value && typeof value === 'object' && k in value) {
                value = value[k];
            } else {
                return key; // Return key if translation not found
            }
        }

        if (typeof value !== 'string') {
            return key;
        }

        // Replace parameters in the translation string
        if (params) {
            return Object.entries(params).reduce(
                (str, [key, val]) => str.replace(`{${key}}`, val),
                value
            );
        }

        return value;
    };

    return {t};
};
