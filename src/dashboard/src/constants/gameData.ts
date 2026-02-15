import {
  Crosshair,
  Dna,
  Eye,
  Flame,
  Gavel,
  Ghost,
  LucideIcon,
  Moon,
  Pill,
  Search,
  Shield,
  ShoppingBag,
  Skull,
  Sparkles,
  Sword,
  Swords,
  User,
  Wine,
  Zap,
} from 'lucide-react';

export interface GameEntityConfig {
  id: string;
  color: string;
  icon: LucideIcon;
  translationKey: string;
  camp?: 'WEREWOLF' | 'GOD' | 'VILLAGER' | 'OTHER';
}

export const GAME_ROLES: Record<string, GameEntityConfig> = {
  WEREWOLF: {
    id: 'WEREWOLF',
    color: '#ef4444', // Red-500
    icon: Skull,
    translationKey: 'roles.labels.WEREWOLF',
    camp: 'WEREWOLF',
  },
  WOLF_KING: {
    id: 'WOLF_KING',
    color: '#b91c1c', // Red-700
    icon: Flame,
    translationKey: 'roles.labels.WOLF_KING',
    camp: 'WEREWOLF',
  },
  WOLF_YOUNGER_BROTHER: {
    id: 'WOLF_YOUNGER_BROTHER',
    color: '#f87171', // Red-400
    icon: Zap,
    translationKey: 'roles.labels.WOLF_YOUNGER_BROTHER',
    camp: 'WEREWOLF',
  },
  WOLF_BROTHER: {
    id: 'WOLF_BROTHER',
    color: '#dc2626', // Red-600
    icon: Skull,
    translationKey: 'roles.labels.WOLF_BROTHER',
    camp: 'WEREWOLF',
  },
  SEER: {
    id: 'SEER',
    color: '#06b6d4', // Cyan-500
    icon: Eye,
    translationKey: 'roles.labels.SEER',
    camp: 'GOD',
  },
  WITCH: {
    id: 'WITCH',
    color: '#a855f7', // Purple-500
    icon: Wine,
    translationKey: 'roles.labels.WITCH',
    camp: 'GOD',
  },
  GUARD: {
    id: 'GUARD',
    color: '#3b82f6', // Blue-500
    icon: Shield,
    translationKey: 'roles.labels.GUARD',
    camp: 'GOD',
  },
  HUNTER: {
    id: 'HUNTER',
    color: '#f97316', // Orange-500
    icon: Swords,
    translationKey: 'roles.labels.HUNTER',
    camp: 'GOD',
  },
  VILLAGER: {
    id: 'VILLAGER',
    color: '#10b981', // Emerald-500
    icon: User,
    translationKey: 'roles.labels.VILLAGER',
    camp: 'VILLAGER',
  },
  IDIOT: {
    id: 'IDIOT',
    color: '#facc15', // Yellow-400
    icon: Gavel,
    translationKey: 'roles.labels.IDIOT',
    camp: 'GOD',
  },
  KNIGHT: {
    id: 'KNIGHT',
    color: '#6366f1', // Indigo-500
    icon: Sword,
    translationKey: 'roles.labels.KNIGHT',
    camp: 'GOD',
  },
  GRAVE_KEEPER: {
    id: 'GRAVE_KEEPER',
    color: '#475569', // Slate-600
    icon: Skull,
    translationKey: 'roles.labels.GRAVE_KEEPER',
    camp: 'GOD',
  },
  DREAM_WEAVER: {
    id: 'DREAM_WEAVER',
    color: '#ec4899', // Pink-500
    icon: Moon,
    translationKey: 'roles.labels.DREAM_WEAVER',
    camp: 'GOD',
  },
  NIGHTMARE: {
    id: 'NIGHTMARE',
    color: '#3b82f6', // Blue-500
    icon: Ghost,
    translationKey: 'roles.labels.NIGHTMARE',
    camp: 'WEREWOLF',
  },
  MAGICIAN: {
    id: 'MAGICIAN',
    color: '#8b5cf6', // Violet-500
    icon: Sparkles,
    translationKey: 'roles.labels.MAGICIAN',
    camp: 'GOD',
  },
  WHITE_WOLF_KING: {
    id: 'WHITE_WOLF_KING',
    color: '#ef4444', // Red-500
    icon: Flame,
    translationKey: 'roles.labels.WHITE_WOLF_KING',
    camp: 'WEREWOLF',
  },
  HIDDEN_WOLF: {
    id: 'HIDDEN_WOLF',
    color: '#dc2626', // Red-600
    icon: Ghost,
    translationKey: 'roles.labels.HIDDEN_WOLF',
    camp: 'WEREWOLF',
  },
  GARGOYLE: {
    id: 'GARGOYLE',
    color: '#991b1b', // Red-800
    icon: Eye,
    translationKey: 'roles.labels.GARGOYLE',
    camp: 'WEREWOLF',
  },
  NIGHT_KNIGHT: {
    id: 'NIGHT_KNIGHT',
    color: '#7f1d1d', // Red-900
    icon: Crosshair,
    translationKey: 'roles.labels.NIGHT_KNIGHT',
    camp: 'WEREWOLF',
  },
  BLOOD_MOON: {
    id: 'BLOOD_MOON',
    color: '#991b1b', // Red-800
    icon: Moon,
    translationKey: 'roles.labels.BLOOD_MOON',
    camp: 'WEREWOLF',
  },
  ROBOT_WOLF: {
    id: 'ROBOT_WOLF',
    color: '#dc2626', // Red-600
    icon: Zap,
    translationKey: 'roles.labels.ROBOT_WOLF',
    camp: 'WEREWOLF',
  },
  CLONE: {
    id: 'CLONE',
    color: '#8b5cf6', // Violet-500
    icon: Dna,
    translationKey: 'roles.labels.CLONE',
    camp: 'GOD',
  },
  DARK_MERCHANT: {
    id: 'DARK_MERCHANT',
    color: '#eab308', // Gold-500
    icon: ShoppingBag,
    translationKey: 'roles.labels.DARK_MERCHANT',
    camp: 'GOD',
  },
  HIDDEN: {
    id: 'HIDDEN',
    color: '#64748b', // Slate-500
    icon: Ghost,
    translationKey: 'roles.labels.HIDDEN',
    camp: 'OTHER',
  },
};

export const GAME_ACTIONS: Record<string, GameEntityConfig> = {
  WEREWOLF_KILL: {
    id: 'WEREWOLF_KILL',
    color: '#ef4444',
    icon: Sword,
    translationKey: 'roles.actions.WEREWOLF_KILL',
  },
  WOLF_YOUNGER_BROTHER_EXTRA_KILL: {
    id: 'WOLF_YOUNGER_BROTHER_EXTRA_KILL',
    color: '#f87171',
    icon: Flame,
    translationKey: 'roles.actions.WOLF_YOUNGER_BROTHER_EXTRA_KILL',
  },
  WITCH_ANTIDOTE: {
    id: 'WITCH_ANTIDOTE',
    color: '#10b981',
    icon: Pill,
    translationKey: 'roles.actions.WITCH_ANTIDOTE',
  },
  WITCH_POISON: {
    id: 'WITCH_POISON',
    color: '#a855f7',
    icon: Pill,
    translationKey: 'roles.actions.WITCH_POISON',
  },
  SEER_CHECK: {
    id: 'SEER_CHECK',
    color: '#06b6d4',
    icon: Search,
    translationKey: 'roles.actions.SEER_CHECK',
  },
  GUARD_PROTECT: {
    id: 'GUARD_PROTECT',
    color: '#f59e0b',
    icon: Shield,
    translationKey: 'roles.actions.GUARD_PROTECT',
  },
  HUNTER_REVENGE: {
    id: 'HUNTER_REVENGE',
    color: '#ec4899',
    icon: Crosshair,
    translationKey: 'roles.actions.HUNTER_REVENGE',
  },
  WOLF_KING_REVENGE: {
    id: 'WOLF_KING_REVENGE',
    color: '#b91c1c',
    icon: Flame,
    translationKey: 'roles.actions.WOLF_KING_REVENGE',
  },
  DARK_MERCHANT_TRADE: {
    id: 'DARK_MERCHANT_TRADE',
    color: '#eab308',
    icon: ShoppingBag,
    translationKey: 'roles.actions.DARK_MERCHANT_TRADE',
  },
  MERCHANT_SEER_CHECK: {
    id: 'MERCHANT_SEER_CHECK',
    color: '#06b6d4',
    icon: Search,
    translationKey: 'roles.actions.MERCHANT_SEER_CHECK',
  },
  MERCHANT_POISON: {
    id: 'MERCHANT_POISON',
    color: '#a855f7',
    icon: Pill,
    translationKey: 'roles.actions.MERCHANT_POISON',
  },
  MERCHANT_GUN: {
    id: 'MERCHANT_GUN',
    color: '#ec4899',
    icon: Crosshair,
    translationKey: 'roles.actions.MERCHANT_GUN',
  },
  DREAM_WEAVER_LINK: {
    id: 'DREAM_WEAVER_LINK',
    color: '#ec4899',
    icon: Moon,
    translationKey: 'roles.actions.DREAM_WEAVER_LINK',
  },
  NIGHTMARE_FEAR: {
    id: 'NIGHTMARE_FEAR',
    color: '#3b82f6',
    icon: Ghost,
    translationKey: 'roles.actions.NIGHTMARE_FEAR',
  },
  DEATH: {
    id: 'DEATH',
    color: '#6b7280',
    icon: Skull,
    translationKey: 'roles.actions.DEATH',
  },
};

// Helper function to get role config by name (handling English/Chinese variants if needed)
export const getRoleConfig = (roleName: string): GameEntityConfig => {
  const upper = roleName.toUpperCase();
  if (GAME_ROLES[upper]) return GAME_ROLES[upper];

  // Fallback for Chinese names if they come from backend directly
  if (roleName.includes('白狼王')) return GAME_ROLES.WHITE_WOLF_KING;
  if (roleName.includes('狼王')) return GAME_ROLES.WOLF_KING;
  if (roleName.includes('狼弟')) return GAME_ROLES.WOLF_YOUNGER_BROTHER;
  if (roleName.includes('狼兄')) return GAME_ROLES.WOLF_BROTHER;
  if (roleName.includes('隱狼')) return GAME_ROLES.HIDDEN_WOLF;
  if (roleName.includes('石像鬼')) return GAME_ROLES.GARGOYLE;
  if (roleName.includes('惡靈騎士')) return GAME_ROLES.NIGHT_KNIGHT;
  if (roleName.includes('血月使者')) return GAME_ROLES.BLOOD_MOON;
  if (roleName.includes('機械狼')) return GAME_ROLES.ROBOT_WOLF;
  if (roleName.includes('狼')) return GAME_ROLES.WEREWOLF;
  if (roleName.includes('預言')) return GAME_ROLES.SEER;
  if (roleName.includes('女巫')) return GAME_ROLES.WITCH;
  if (roleName.includes('守衛')) return GAME_ROLES.GUARD;
  if (roleName.includes('獵人')) return GAME_ROLES.HUNTER;
  if (roleName.includes('白痴')) return GAME_ROLES.IDIOT;
  if (roleName.includes('騎士')) return GAME_ROLES.KNIGHT;
  if (roleName.includes('守墓人')) return GAME_ROLES.GRAVE_KEEPER;

  if (roleName.includes('攝夢人')) return GAME_ROLES.DREAM_WEAVER;
  if (roleName.includes('夢魘')) return GAME_ROLES.NIGHTMARE;
  if (roleName.includes('魔術師')) return GAME_ROLES.MAGICIAN;
  if (roleName.includes('平民') || roleName.includes('村民')) return GAME_ROLES.VILLAGER;
  if (roleName.includes('黑市')) return GAME_ROLES.DARK_MERCHANT;
  if (roleName.includes('複製')) return GAME_ROLES.CLONE;

  return GAME_ROLES.HIDDEN;
};

// Helper function to get action config by ID
export const getActionConfig = (actionId: string): GameEntityConfig => {
  const config = Object.values(GAME_ACTIONS).find((a) => actionId.includes(a.id));
  if (config) return config;

  return {
    id: 'UNKNOWN',
    color: '#6b7280',
    icon: Gavel,
    translationKey: 'roles.actions.UNKNOWN',
  };
};
