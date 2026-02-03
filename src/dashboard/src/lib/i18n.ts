import zhTW from '../locales/zh-TW.json';

type TranslationKey = string;
type Translations = typeof zhTW;

// Simple i18n without external dependencies
const translations: Translations = zhTW;

export const useTranslation = () => {
    const t = (
        key: TranslationKey,
        fallbackOrParams?: string | Record<string, string>,
        params?: Record<string, string>
    ): string => {
        const keys = key.split('.');
        let value: any = translations;

        const fallback = typeof fallbackOrParams === 'string' ? fallbackOrParams : undefined;
        const variables = typeof fallbackOrParams === 'object' ? fallbackOrParams : params;

        for (const k of keys) {
            if (value && typeof value === 'object' && k in value) {
                value = value[k];
            } else {
                const base = fallback ?? key;
                return variables
                    ? Object.entries(variables).reduce(
                        (str, [paramKey, val]) => str.replace(new RegExp(`\\{${paramKey}\\}`, 'g'), val),
                        base
                    )
                    : base;
            }
        }

        if (typeof value !== 'string') {
            const base = fallback ?? key;
            return variables
                ? Object.entries(variables).reduce(
                    (str, [paramKey, val]) => str.replace(new RegExp(`\\{${paramKey}\\}`, 'g'), val),
                    base
                )
                : base;
        }

        if (variables) {
            return Object.entries(variables).reduce(
                (str, [paramKey, val]) => str.replace(new RegExp(`\\{${paramKey}\\}`, 'g'), val),
                value
            );
        }

        return value;
    };

    return {t};
};
