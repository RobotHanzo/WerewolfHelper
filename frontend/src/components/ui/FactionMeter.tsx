import { useTranslation } from "react-i18next";
import type { FactionMeter as FactionMeterData } from "@/types/snapshot";

const COLOR: Record<string, string> = {
  WOLF: "var(--wolf-500)",
  GOD: "var(--god-500)",
  VILLAGER: "var(--vill-500)",
  GBABY: "var(--gbaby-amber)",
};
const GLYPH: Record<string, string> = { WOLF: "狼", GOD: "神", VILLAGER: "民", GBABY: "寶" };
const LABEL_KEY: Record<string, string> = {
  WOLF: "faction.wolfFull",
  GOD: "faction.godFull",
  VILLAGER: "faction.villFull",
  GBABY: "faction.gbaby",
};

export function FactionMeter({ meter }: { meter: FactionMeterData }) {
  const { t } = useTranslation();
  const color = COLOR[meter.faction];
  const pct = meter.total > 0 ? (meter.alive / meter.total) * 100 : 0;
  return (
    <div className="wh-meter">
      <div className="wh-meter__head">
        <span style={{ color, fontWeight: 900 }}>{GLYPH[meter.faction]}</span>
        <span style={{ fontWeight: 700 }}>{t(LABEL_KEY[meter.faction])}</span>
        <span className="wh-meter__count" style={{ color }}>
          {meter.alive} / {meter.total}
        </span>
      </div>
      <span className="wh-progress" style={{ display: "block", height: 8 }}>
        <span className="wh-progress__fill" style={{ width: `${pct}%`, background: color }} />
      </span>
    </div>
  );
}
