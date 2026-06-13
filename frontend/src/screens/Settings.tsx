import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/api/client";
import { useGameStore } from "@/stores/gameStore";
import { useGameActions } from "@/hooks/useGameActions";
import { useGuild } from "@/hooks/useGuild";
import { Switch } from "@/components/ui/Switch";
import { Stepper } from "@/components/ui/Stepper";
import { Button } from "@/components/ui/Button";
import { FactionBadge, RoleIcon } from "@/components/ui/Badge";
import type { Faction, RoleInfo } from "@/types/snapshot";

const factionNames: Record<string, string> = {
  WOLF: "狼人陣營",
  GOD: "神職陣營",
  VILLAGER: "平民陣營",
  GBABY: "金寶寶",
};

const factionColors: Record<string, { border: string; bg: string; text: string }> = {
  WOLF: {
    border: "rgba(240, 74, 94, 0.25)",
    bg: "var(--wolf-dim)",
    text: "var(--wolf-400)",
  },
  GOD: {
    border: "rgba(139, 123, 244, 0.25)",
    bg: "var(--god-dim)",
    text: "var(--god-400)",
  },
  VILLAGER: {
    border: "rgba(54, 201, 125, 0.25)",
    bg: "var(--vill-dim)",
    text: "var(--vill-400)",
  },
  GBABY: {
    border: "rgba(255, 159, 69, 0.25)",
    bg: "var(--gbaby-amber-dim)",
    text: "var(--gbaby-amber)",
  },
};

export function Settings() {
  const { t } = useTranslation();
  const { guildId, demo } = useGuild();
  const snapshot = useGameStore((s) => s.snapshot);
  const actions = useGameActions(guildId, demo);
  const [count, setCount] = useState(snapshot?.totalSeats ?? 12);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [pool, setPool] = useState<Record<string, number>>({});

  useEffect(() => {
    api.roles().then(setRoles).catch(() => setRoles([]));
  }, []);

  useEffect(() => {
    if (snapshot) {
      setCount(snapshot.totalSeats);
      if (snapshot.pool) {
        setPool(snapshot.pool);
      }
    }
  }, [snapshot]);

  const need = snapshot?.doubleIdentity ? count * 2 : count;
  const total = useMemo(() => Object.values(pool).reduce((a, b) => a + b, 0), [pool]);
  const mismatch = total !== need;
  const roleById = useMemo(() => new Map(roles.map((r) => [r.id, r])), [roles]);
  const dirty = snapshot != null && count !== snapshot.totalSeats;

  const rolesByFaction = useMemo(() => {
    const groups: Record<string, RoleInfo[]> = {};
    roles.forEach((r) => {
      const f = r.faction || "UNKNOWN";
      if (!groups[f]) groups[f] = [];
      groups[f].push(r);
    });
    return groups;
  }, [roles]);

  const updatePool = (newPool: Record<string, number>) => {
    const cleaned = Object.fromEntries(
      Object.entries(newPool).filter(([_, c]) => c > 0)
    );
    setPool(cleaned);
    actions.setPool(cleaned);
  };

  if (!snapshot) return null;

  return (
    <div style={{ maxWidth: 880, margin: "0 auto", display: "flex", flexDirection: "column", gap: 16 }}>
      <style>{`
        .wh-role-btn {
          transition: all var(--dur-fast) var(--ease-out);
          cursor: pointer;
        }
        .wh-role-btn:hover {
          transform: translateY(-1px);
          filter: brightness(1.15);
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
        }
        .wh-role-btn:active {
          transform: translateY(0);
        }
      `}</style>

      <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900 }}>{t("settings.title")}</h1>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, alignItems: "start" }}>
        
        {/* left column */}
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
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

          <section className="wh-card" style={{ padding: 16, display: "flex", flexDirection: "column", gap: 14 }}>
            <h2 className="wh-section-title">{t("settings.addIdentity")}</h2>
            
            {Object.entries(rolesByFaction).map(([faction, factionRoles]) => {
              if (factionRoles.length === 0) return null;
              const fColors = factionColors[faction] || { border: "var(--border-2)", bg: "var(--surface-raised)", text: "var(--text-secondary)" };
              const fName = factionNames[faction] || faction;
              return (
                <div key={faction} style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  <span style={{ fontSize: 11, fontWeight: 700, color: fColors.text, letterSpacing: "0.08em" }}>
                    {fName}
                  </span>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                    {factionRoles.map((r) => (
                      <button
                        key={r.id}
                        className="wh-btn wh-btn--sm wh-role-btn"
                        style={{
                          background: fColors.bg,
                          color: "var(--text-body)",
                          borderColor: fColors.border,
                          borderWidth: 1,
                          borderStyle: "solid",
                          display: "inline-flex",
                          alignItems: "center",
                          gap: 4,
                        }}
                        onClick={() => updatePool({ ...pool, [r.id]: (pool[r.id] ?? 0) + 1 })}
                      >
                        ＋ <RoleIcon roleId={r.id} size={12} /> {r.name}
                      </button>
                    ))}
                  </div>
                </div>
              );
            })}
          </section>
        </div>

        {/* right column */}
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
                  <FactionBadge faction={(role?.faction ?? "GOD") as Faction} roleId={id} name={role?.name ?? id} />
                  <span style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 6 }}>
                    <button className="wh-btn wh-btn--secondary wh-btn--sm" onClick={() => updatePool({ ...pool, [id]: Math.max(0, c - 1) })}>−</button>
                    <span className="mono" style={{ width: 30, textAlign: "center", fontWeight: 700 }}>×{c}</span>
                    <button className="wh-btn wh-btn--secondary wh-btn--sm" onClick={() => updatePool({ ...pool, [id]: c + 1 })}>＋</button>
                    <button className="wh-btn wh-btn--ghost wh-btn--sm" onClick={() => { const n = { ...pool }; delete n[id]; updatePool(n); }}>✕</button>
                  </span>
                </div>
              );
            })}
          </div>
          <Button variant="secondary" size="sm" style={{ alignSelf: "flex-start" }} onClick={actions.assign}>{t("dashboard.cmd.assign")}</Button>
        </section>

      </div>
    </div>
  );
}
