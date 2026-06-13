import { useTranslation } from "react-i18next";
import type { Faction } from "@/types/snapshot";

type Size = "sm" | "md";

/** Faction glyph — color is never the only signal (狼/神/民 carried explicitly). */
const FACTION_GLYPH: Record<string, string> = { WOLF: "狼", GOD: "神", VILLAGER: "民", GBABY: "寶" };
const FACTION_CLASS: Record<string, string> = { WOLF: "wolf", GOD: "god", VILLAGER: "vill", GBABY: "gbaby" };

export function FactionBadge({
  faction,
  name,
  dead,
  size = "md",
}: {
  faction: Faction;
  name: string;
  dead?: boolean;
  size?: Size;
}) {
  const cls = ["wh-badge", `wh-badge--${FACTION_CLASS[faction]}`, `wh-badge--${size}`, dead ? "wh-badge--dead" : ""].join(" ");
  return (
    <span className={cls}>
      <span className="wh-badge__glyph">{FACTION_GLYPH[faction]}</span>
      {name}
    </span>
  );
}

type StateKind = "police" | "gbaby" | "lock" | "dead";

export function StateBadge({ kind, size = "md" }: { kind: StateKind; size?: Size }) {
  const { t } = useTranslation();
  const cls = ["wh-badge", `wh-badge--${kind === "dead" ? "lock" : kind}`, `wh-badge--${size}`].join(" ");
  const label: Record<StateKind, string> = {
    police: `⛨ ${t("badge.police")}`,
    gbaby: t("badge.gbaby"),
    lock: `鎖 ${t("badge.locked")}`,
    dead: t("badge.dead"),
  };
  return <span className={cls}>{label[kind]}</span>;
}
