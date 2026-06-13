import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/api/client";
import { useGameStore } from "@/stores/gameStore";
import { useGameActions } from "@/hooks/useGameActions";
import { useGuild } from "@/hooks/useGuild";
import { Switch } from "@/components/ui/Switch";
import { Stepper } from "@/components/ui/Stepper";
import { Button } from "@/components/ui/Button";
import { FactionBadge } from "@/components/ui/Badge";
import type { Faction, RoleInfo } from "@/types/snapshot";

export function Settings() {
  const { t } = useTranslation();
  const { guildId, demo } = useGuild();
  const snapshot = useGameStore((s) => s.snapshot);
  const actions = useGameActions(guildId, demo);
  const [count, setCount] = useState(snapshot?.totalSeats ?? 12);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [pool, setPool] = useState<Record<string, number>>({});
  const [query, setQuery] = useState("");

  useEffect(() => {
    api.roles().then(setRoles).catch(() => setRoles([]));
  }, []);
  useEffect(() => {
    if (snapshot) setCount(snapshot.totalSeats);
  }, [snapshot]);

  const need = snapshot?.doubleIdentity ? count * 2 : count;
  const total = useMemo(() => Object.values(pool).reduce((a, b) => a + b, 0), [pool]);
  const mismatch = total !== need;
  const roleById = useMemo(() => new Map(roles.map((r) => [r.id, r])), [roles]);
  const dirty = snapshot != null && count !== snapshot.totalSeats;

  const suggestions = roles.filter((r) => query && r.name.includes(query) && !(r.id in pool)).slice(0, 6);

  if (!snapshot) return null;

  return (
    <div style={{ maxWidth: 880, margin: "0 auto", display: "flex", flexDirection: "column", gap: 16 }}>
      <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900 }}>{t("settings.title")}</h1>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, alignItems: "start" }}>
        <section className="wh-card" style={{ padding: 16, display: "flex", flexDirection: "column", gap: 14 }}>
          <h2 className="wh-section-title">{t("settings.options")}</h2>
          <Switch checked={snapshot.doubleIdentity} onChange={actions.setDoubleIdentity} label={t("settings.doubleIdentity")} description={t("settings.doubleIdentityDesc")} />
          <Switch checked={snapshot.muteAfterSpeech} onChange={actions.setMuteAfterSpeech} label={t("settings.muteAfterSpeech")} description={t("settings.muteAfterSpeechDesc")} />
          <div style={{ borderTop: "1px solid var(--border-1)", paddingTop: 14, display: "flex", flexDirection: "column", gap: 10 }}>
            <Stepper label={t("settings.playerCount")} value={count} min={4} max={20} onChange={setCount} dirty={dirty} />
            {dirty && (
              <Button variant="primary" size="sm" style={{ alignSelf: "flex-start" }} onClick={() => actions.setPlayerCount(count)}>
                {t("settings.applyCount")}
              </Button>
            )}
          </div>
        </section>

        <section className="wh-card" style={{ padding: 16, display: "flex", flexDirection: "column", gap: 12 }}>
          <header style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <h2 className="wh-section-title">{t("settings.pool")}</h2>
            <span className="mono" style={{ fontSize: 12, fontWeight: 700, color: mismatch ? "var(--warning-500)" : "var(--success-500)" }}>
              {t("settings.poolTotal", { total, need })}
            </span>
          </header>
          {mismatch && (
            <div style={{ display: "flex", gap: 8, alignItems: "center", padding: "8px 12px", borderRadius: "var(--r-sm)", background: "var(--warning-dim)", border: "1px solid rgba(255,180,84,0.35)", fontSize: 12, color: "var(--warning-500)", fontWeight: 700 }}>
              ⚠ {t("settings.poolMismatch")}
            </div>
          )}
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {Object.entries(pool).map(([id, c]) => {
              const role = roleById.get(id);
              return (
                <div key={id} style={{ display: "flex", alignItems: "center", gap: 8, padding: "7px 10px", borderRadius: "var(--r-md)", background: "var(--surface-app)", border: "1px solid var(--border-1)" }}>
                  <FactionBadge faction={(role?.faction ?? "GOD") as Faction} name={role?.name ?? id} />
                  <span style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 6 }}>
                    <button className="wh-btn wh-btn--secondary wh-btn--sm" onClick={() => setPool((p) => ({ ...p, [id]: Math.max(0, c - 1) }))}>−</button>
                    <span className="mono" style={{ width: 30, textAlign: "center", fontWeight: 700 }}>×{c}</span>
                    <button className="wh-btn wh-btn--secondary wh-btn--sm" onClick={() => setPool((p) => ({ ...p, [id]: c + 1 }))}>＋</button>
                    <button className="wh-btn wh-btn--ghost wh-btn--sm" onClick={() => setPool((p) => { const n = { ...p }; delete n[id]; return n; })}>✕</button>
                  </span>
                </div>
              );
            })}
          </div>
          <input className="wh-input" placeholder={t("settings.addIdentity")} value={query} onChange={(e) => setQuery(e.target.value)} />
          {suggestions.length > 0 && (
            <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
              {suggestions.map((r) => (
                <button key={r.id} className="wh-btn wh-btn--ghost wh-btn--sm" style={{ borderStyle: "dashed", borderColor: "var(--border-2)" }} onClick={() => { setPool((p) => ({ ...p, [r.id]: (p[r.id] ?? 0) + 1 })); setQuery(""); }}>
                  ＋ {r.name}
                </button>
              ))}
            </div>
          )}
          <Button variant="secondary" size="sm" style={{ alignSelf: "flex-start" }} onClick={actions.assign}>{t("dashboard.cmd.assign")}</Button>
        </section>
      </div>
    </div>
  );
}
