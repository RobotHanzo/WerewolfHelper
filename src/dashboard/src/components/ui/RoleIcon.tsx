import { Eye, Moon, Shield, Swords, Users, Zap } from 'lucide-react';
import { Role } from '@/types';

export const RoleIcon = ({ role }: { role: Role }) => {
  switch (role) {
    case 'WEREWOLF':
      return <Moon className="w-4 h-4 text-red-400" />;
    case 'SEER':
      return <Eye className="w-4 h-4 text-purple-400" />;
    case 'WITCH':
      return <Zap className="w-4 h-4 text-pink-400" />;
    case 'HUNTER':
      return <Swords className="w-4 h-4 text-orange-400" />;
    case 'GUARD':
      return <Shield className="w-4 h-4 text-blue-400" />;
    case 'VILLAGER':
      return <Users className="w-4 h-4 text-emerald-400" />;
    default:
      return <Users className="w-4 h-4 text-slate-400" />;
  }
};
