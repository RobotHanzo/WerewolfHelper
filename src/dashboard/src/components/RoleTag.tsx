import React from 'react';
import { useTranslation } from '@/lib/i18n';
import { getRoleConfig } from '@/constants/gameData';

interface RoleTagProps {
  roleName: string;
  index?: number;
  showIndex?: boolean;
  isDead?: boolean;
  onClick?: () => void;
  className?: string;
}

export const RoleTag: React.FC<RoleTagProps> = ({
  roleName,
  index,
  showIndex = false,
  isDead = false,
  onClick,
  className = '',
}) => {
  const { t } = useTranslation();
  const config = getRoleConfig(roleName);

  const baseStyles =
    'text-[10px] uppercase tracking-wider font-bold px-1.5 py-0.5 rounded border transition-all flex items-center gap-1';

  const colorStyles =
    config.camp === 'WEREWOLF'
      ? 'bg-red-100 dark:bg-red-900/40 border-red-300 dark:border-red-800 text-red-700 dark:text-red-300'
      : config.camp === 'GOD'
        ? 'bg-indigo-100 dark:bg-indigo-900/40 border-indigo-300 dark:border-indigo-800 text-indigo-700 dark:text-indigo-300'
        : config.camp === 'VILLAGER'
          ? 'bg-emerald-100 dark:bg-emerald-900/40 border-emerald-300 dark:border-emerald-800 text-emerald-700 dark:text-emerald-300'
          : 'bg-slate-100 dark:bg-slate-800/40 border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300';

  const statusStyles = isDead ? 'line-through opacity-60 decoration-2 decoration-slate-500' : '';

  const interactionStyles = onClick ? 'cursor-pointer hover:opacity-100' : '';

  return (
    <span
      onClick={onClick}
      className={`${baseStyles} ${colorStyles} ${statusStyles} ${interactionStyles} ${className}`}
    >
      <config.icon className="w-3 h-3" />
      {showIndex && index !== undefined && `${index + 1}. `}
      {t(config.translationKey, config.id.replace(/_/g, ' '))}
    </span>
  );
};
