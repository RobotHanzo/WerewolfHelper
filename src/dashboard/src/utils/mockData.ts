import { Player } from '@/types';

export const MOCK_AVATARS = [
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Zack',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Sarah',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=John',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Mila',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Leo',
  'https://api.dicebear.com/7.x/avataaars/svg?seed=Kai',
];

export const INITIAL_PLAYERS: Player[] = Array.from({ length: 8 }).map((_, i) => ({
  id: i + 1,
  name: `Player ${i + 1}`,
  avatar: MOCK_AVATARS[i],
  roles: i === 0 ? ['WEREWOLF'] : i === 1 ? ['SEER'] : i === 2 ? ['WITCH'] : ['VILLAGER'],
  deadRoles: [],
  isAlive: true,
  isSheriff: false,
  isJinBaoBao: i === 3,
  isProtected: false,
  isPoisoned: false,
  isSilenced: false,
}));
