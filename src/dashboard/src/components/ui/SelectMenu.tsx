import React from 'react';
import { ChevronDown } from 'lucide-react';

export interface SelectOption {
  value: string | number;
  label: React.ReactNode;
}

interface SelectMenuProps {
  value: string | number;
  onChange: (value: string | number) => void;
  options: SelectOption[];
  className?: string;
}

export const SelectMenu: React.FC<SelectMenuProps> = ({
  value,
  onChange,
  options,
  className = '',
}) => {
  return (
    <div className={`relative inline-block min-w-[120px] ${className}`}>
      <select
        value={value}
        onChange={(e) => {
          const val = e.target.value;
          // Try to preserve numeric types if the options value type is numeric
          const option = options.find((o) => String(o.value) === val);
          if (option) {
            onChange(option.value);
          } else {
            onChange(val);
          }
        }}
        className="w-full bg-slate-800 text-white border border-slate-700 rounded-lg pl-4 pr-10 py-2 focus:ring-2 focus:ring-indigo-500 outline-none font-semibold cursor-pointer appearance-none shadow-inner"
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <div className="absolute inset-y-0 right-0 flex items-center px-3 pointer-events-none text-slate-400">
        <ChevronDown className="w-4 h-4" />
      </div>
    </div>
  );
};
