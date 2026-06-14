import { motion } from "framer-motion";
import { useTranslation } from "react-i18next";
import type { Phase, Seat } from "@/types/snapshot";
import { Avatar } from "./Avatar";
import { FactionBadge, StateBadge } from "./Badge";
import { Button } from "./Button";

interface PlayerCardProps {
  seat: Seat;
  changed?: boolean;
  readOnly?: boolean;
  phase?: Phase;
  /** Localized name of a 機械狼's learned identity, if any. */
  learnedRoleName?: string;
  /** When set, the whole card is a click target for the active revenge/duel pick. */
  targeting?: boolean;
  onKill?: (identityIndex: number, identityName: string) => void;
  onRevive?: () => void;
  onEdit?: () => void;
  /** Click a struck-through dead identity to revive just it. */
  onReviveIdentity?: (identityIndex: number) => void;
  onRevenge?: () => void;
  onDuel?: () => void;
  onSelfDestruct?: () => void;
  onPickTarget?: () => void;
}

/**
 * The seat unit: avatar, seat name, identities in order (per-identity death strike, click a dead
 * identity to revive it), 警長/金寶寶/lock + ROLES.md state badges, and judge actions (kill / revive
 * / edit plus 開槍 / 決鬥 / 自爆). Flashes `.wh-changed` when the seat changed remotely; dims when
 * fully dead. While a revenge/duel target is being picked, the card becomes a target button.
 */
export function PlayerCard({
  seat, changed, readOnly, phase, learnedRoleName, targeting,
  onKill, onRevive, onEdit, onReviveIdentity, onRevenge, onDuel, onSelfDestruct, onPickTarget,
}: PlayerCardProps) {
  const { t } = useTranslation();
  const fullyDead = !seat.alive && !seat.unassigned;
  const anyDead = seat.identities.some((i) => i.dead);
  const living = seat.identities.filter((i) => !i.dead);
  const isWolf = living.some((i) => i.faction === "WOLF");
  const isKnight = living.some((i) => i.roleId === "knight");

  const canSelfDestruct = !readOnly && !fullyDead && !seat.unassigned && isWolf;
  const canDuel = !readOnly && !fullyDead && isKnight && phase === "SPEECHES";
  const canRevenge = !readOnly && seat.revengePending;

  return (
    <motion.div
      layout
      className={`wh-card ${changed ? "wh-changed" : ""}`}
      onClick={targeting && !fullyDead && !seat.unassigned ? onPickTarget : undefined}
      style={{
        padding: 14, display: "flex", flexDirection: "column", gap: 10,
        opacity: fullyDead ? 0.6 : 1,
        cursor: targeting && !fullyDead && !seat.unassigned ? "crosshair" : "default",
        outline: targeting && !fullyDead && !seat.unassigned ? "2px dashed var(--wolf-400)" : "none",
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <Avatar name={seat.displayName} avatar={seat.avatar} size="md" dead={fullyDead} unassigned={seat.unassigned} />
        <div style={{ display: "flex", flexDirection: "column", minWidth: 0, flex: 1 }}>
          <span className="mono" style={{ fontWeight: 700, fontSize: 15, textDecoration: fullyDead ? "line-through" : "none" }}>
            玩家{seat.label}
          </span>
          <span style={{ fontSize: 12, color: "var(--text-muted)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
            {seat.unassigned ? "—" : seat.displayName}
          </span>
        </div>
        <div style={{ display: "flex", gap: 4, flexWrap: "wrap", justifyContent: "flex-end" }}>
          {seat.police && <StateBadge kind="police" size="sm" />}
          {seat.goldenBaby && <StateBadge kind="gbaby" size="sm" />}
          {seat.identities.length > 1 && seat.orderLocked && <StateBadge kind="lock" size="sm" />}
          {seat.revengePending && <StateBadge kind="revenge" size="sm" />}
          {seat.idiotRevealed && <StateBadge kind="idiotRevealed" size="sm" />}
          {seat.knifeArmed && <StateBadge kind="knife" size="sm" />}
          {seat.charmedSeat != null && <StateBadge kind="charmed" size="sm" />}
          {seat.loverSeat != null && <StateBadge kind="lover" size="sm" />}
          {seat.learnedRoleId && <StateBadge kind="learned" size="sm" text={learnedRoleName} />}
        </div>
      </div>

      <div style={{ display: "flex", gap: 4, flexWrap: "wrap", minHeight: 22 }}>
        {seat.identities.length === 0 && <span style={{ fontSize: 12, color: "var(--text-disabled)" }}>—</span>}
        {seat.identities.map((idn, i) => (
          <span
            key={i}
            onClick={() => !readOnly && idn.dead && onReviveIdentity?.(i)}
            style={{ cursor: !readOnly && idn.dead ? "pointer" : "default" }}
            title={!readOnly && idn.dead ? t("dashboard.seatAction.revive") : undefined}
          >
            <FactionBadge faction={idn.faction} roleId={idn.roleId} name={idn.name} dead={idn.dead} size="sm" />
          </span>
        ))}
      </div>

      {!readOnly && (
        <div style={{ display: "flex", gap: 4, marginTop: "auto", flexWrap: "wrap" }}>
          <Button size="sm" variant="danger" disabled={fullyDead || seat.unassigned} style={{ flex: 1 }} onClick={() => {
            const idx = seat.identities.findIndex((x) => !x.dead);
            if (idx >= 0) onKill?.(idx, seat.identities[idx].name);
          }}>
            {t("dashboard.seatAction.kill")}
          </Button>
          {anyDead && (
            <Button size="sm" variant="secondary" style={{ flex: 1 }} onClick={onRevive}>
              {t("dashboard.seatAction.revive")}
            </Button>
          )}
          <Button size="sm" variant="ghost" style={{ flex: 1 }} onClick={onEdit}>
            {t("dashboard.seatAction.edit")}
          </Button>
          {canRevenge && (
            <Button size="sm" variant="danger" style={{ flex: 1 }} onClick={onRevenge}>
              {t("dashboard.seatAction.revenge")}
            </Button>
          )}
          {canDuel && (
            <Button size="sm" variant="secondary" style={{ flex: 1 }} onClick={onDuel}>
              {t("dashboard.seatAction.duel")}
            </Button>
          )}
          {canSelfDestruct && (
            <Button size="sm" variant="ghost" armed armedLabel={t("dashboard.seatAction.selfDestruct")} style={{ flex: 1 }} onClick={onSelfDestruct}>
              {t("dashboard.seatAction.selfDestruct")}
            </Button>
          )}
        </div>
      )}
    </motion.div>
  );
}
