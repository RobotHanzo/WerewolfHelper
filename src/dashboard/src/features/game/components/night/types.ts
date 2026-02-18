import { RoleActionInstance } from '@/api/types.gen';

export interface EnrichedActionStatus extends RoleActionInstance {
  playerName: string;
  playerUserId?: string | number | bigint;
  targetName: string | null;
  targetUserId?: string | number | bigint;
  targetRole?: string;
  actionName: string | null;
}
