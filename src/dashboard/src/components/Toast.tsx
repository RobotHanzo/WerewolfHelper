import React, {useEffect} from 'react';
import {AlertCircle, CheckCircle2, X, XCircle} from 'lucide-react';

export interface ToastMessage {
    id: string;
    type: 'success' | 'error' | 'info';
    message: string;
    duration?: number;
}

interface ToastProps {
    toast: ToastMessage;
    onClose: () => void;
}

const Toast: React.FC<ToastProps> = ({toast, onClose}) => {
    useEffect(() => {
        if (toast.duration !== 0) {
            const timer = setTimeout(onClose, toast.duration || 4000);
            return () => clearTimeout(timer);
        }
    }, [toast, onClose]);

    const bgColor = {
        success: 'bg-emerald-50 dark:bg-emerald-900/30 border-emerald-200 dark:border-emerald-800',
        error: 'bg-red-50 dark:bg-red-900/30 border-red-200 dark:border-red-800',
        info: 'bg-blue-50 dark:bg-blue-900/30 border-blue-200 dark:border-blue-800',
    };

    const textColor = {
        success: 'text-emerald-800 dark:text-emerald-200',
        error: 'text-red-800 dark:text-red-200',
        info: 'text-blue-800 dark:text-blue-200',
    };

    const Icon = {
        success: CheckCircle2,
        error: XCircle,
        info: AlertCircle,
    }[toast.type];

    return (
        <div
            className={`
                flex items-start gap-3 p-4 rounded-lg border shadow-lg
                animate-in fade-in slide-in-from-top-2 duration-200
                ${bgColor[toast.type]}
            `}
        >
            <Icon className="w-5 h-5 mt-0.5 flex-shrink-0"/>
            <div className={`flex-1 ${textColor[toast.type]}`}>
                <p className="font-medium">{toast.message}</p>
            </div>
            <button
                onClick={onClose}
                className={`flex-shrink-0 rounded-full p-1 hover:bg-black/10 transition-colors`}
            >
                <X className="w-4 h-4"/>
            </button>
        </div>
    );
};

interface ToastContainerProps {
    toasts: ToastMessage[];
    onRemove: (id: string) => void;
}

export const ToastContainer: React.FC<ToastContainerProps> = ({toasts, onRemove}) => {
    return (
        <div className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 max-w-md">
            {toasts.map(toast => (
                <Toast
                    key={toast.id}
                    toast={toast}
                    onClose={() => onRemove(toast.id)}
                />
            ))}
        </div>
    );
};

export const useToast = () => {
    const [toasts, setToasts] = React.useState<ToastMessage[]>([]);

    const addToast = (type: 'success' | 'error' | 'info', message: string, duration?: number) => {
        const id = Math.random().toString(36).substr(2, 9);
        setToasts(prev => [...prev, {id, type, message, duration}]);
        return id;
    };

    const removeToast = (id: string) => {
        setToasts(prev => prev.filter(t => t.id !== id));
    };

    return {
        toasts,
        addToast,
        removeToast,
        success: (message: string) => addToast('success', message),
        error: (message: string) => addToast('error', message),
        info: (message: string) => addToast('info', message),
    };
};
