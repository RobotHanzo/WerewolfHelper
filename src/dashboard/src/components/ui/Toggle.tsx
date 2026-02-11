import React from 'react';
import { Loader2 } from 'lucide-react';

interface ToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
  loading?: boolean;
  className?: string;
}

export const Toggle: React.FC<ToggleProps> = ({
  checked,
  onChange,
  disabled = false,
  loading = false,
  className = '',
}) => {
  return (
    <div className={`flex items-center gap-3 ${className}`}>
      {loading && <Loader2 className="w-4 h-4 animate-spin text-indigo-500" />}
      <label
        className={`relative inline-flex items-center ${disabled || loading ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'}`}
      >
        <input
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          disabled={disabled || loading}
          className="sr-only peer"
        />
        <div className="w-11 h-6 bg-slate-200 peer-focus:outline-none rounded-full dark:bg-slate-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all after:duration-200 dark:border-gray-600 peer-checked:bg-indigo-600 transition-colors duration-200"></div>
      </label>
    </div>
  );
};
