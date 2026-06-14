import { useTranslation } from "react-i18next";
import type { Faction } from "@/types/snapshot";
import {
  FlaskConical,
  Target,
  Eye,
  User,
  Crown,
  Heart,
  Sparkles,
  Moon,
  Dna,
  Shield,
  Sword,
  Smile,
  Ghost,
  Zap,
  Coins,
  HeartHandshake,
  Key,
  Users,
  UserMinus,
  Copy,
  Droplet,
  Swords,
  Compass,
  Cpu,
  Crosshair,
  ShieldAlert,
  EyeOff
} from "lucide-react";

type Size = "sm" | "md";

const FACTION_CLASS: Record<string, string> = { WOLF: "wolf", GOD: "god", VILLAGER: "vill", GBABY: "gbaby" };

export function RoleIcon({ roleId, size = 14 }: { roleId: string; size?: number }) {
  const iconProps = { size, className: "wh-role-icon", style: { display: "inline-block", verticalAlign: "middle" } };

  switch (roleId.toLowerCase()) {
    // Factions
    case "god":
      return <Shield {...iconProps} />;
    case "vill":
    case "villager":
      return <User {...iconProps} />;
    case "gbaby":
      return <Crown {...iconProps} />;

    // Predefined roles
    case "wolf":
      return <WolfSvg {...iconProps} />;
    case "witch":
      return <FlaskConical {...iconProps} />;
    case "hunter":
      return <Target {...iconProps} />;
    case "seer":
      return <Eye {...iconProps} />;
    case "wolf_king":
      return <Crown {...iconProps} />;
    case "wolf_beauty":
      return <Heart {...iconProps} />;
    case "white_wolf_king":
      return <Sparkles {...iconProps} />;
    case "nightmare":
      return <Moon {...iconProps} />;
    case "hybrid":
      return <Dna {...iconProps} />;
    case "guard":
      return <Shield {...iconProps} />;
    case "knight":
      return <Sword {...iconProps} />;
    case "idiot":
      return <Smile {...iconProps} />;
    case "gravekeeper":
      return <Ghost {...iconProps} />;
    case "magician":
      return <Zap {...iconProps} />;
    case "black_merchant":
      return <Coins {...iconProps} />;
    case "cupid":
      return <HeartHandshake {...iconProps} />;
    case "thief":
      return <Key {...iconProps} />;
    case "gargoyle":
      return <ShieldAlert {...iconProps} />;
    case "wolf_brother":
      return <Users {...iconProps} />;
    case "wolf_younger":
      return <UserMinus {...iconProps} />;
    case "clone":
      return <Copy {...iconProps} />;
    case "blood_moon":
      return <Droplet {...iconProps} />;
    case "evil_knight":
      return <Swords {...iconProps} />;
    case "psychic":
      return <Compass {...iconProps} />;
    case "mechanic_wolf":
      return <Cpu {...iconProps} />;
    case "demon_hunter":
      return <Crosshair {...iconProps} />;
    case "hidden_wolf":
      return <EyeOff {...iconProps} />;
    default:
      return <User {...iconProps} />;
  }
}

export function FactionBadge({
  faction,
  roleId,
  name,
  dead,
  size = "md",
}: {
  faction: Faction;
  roleId?: string;
  name: string;
  dead?: boolean;
  size?: Size;
}) {
  const cls = ["wh-badge", `wh-badge--${FACTION_CLASS[faction]}`, `wh-badge--${size}`, dead ? "wh-badge--dead" : ""].join(" ");
  return (
    <span className={cls}>
      <span className="wh-badge__glyph" style={{ display: "inline-flex", alignItems: "center", justifyContent: "center" }}>
        <RoleIcon roleId={roleId || faction} size={size === "sm" ? 11 : 13} />
      </span>
      {name}
    </span>
  );
}

type StateKind = "police" | "gbaby" | "lock" | "dead" | "revenge" | "idiotRevealed" | "knife" | "charmed" | "lover" | "learned";

export function StateBadge({ kind, size = "md", text }: { kind: StateKind; size?: Size; text?: string }) {
  const { t } = useTranslation();
  // Reuse existing faction-tinted chip styles for the new ROLES.md state badges.
  const styleKind =
    kind === "revenge" || kind === "knife" ? "wolf"
      : kind === "charmed" || kind === "lover" ? "gbaby"
      : kind === "idiotRevealed" || kind === "learned" ? "god"
      : kind === "dead" ? "lock"
      : kind;
  const cls = ["wh-badge", `wh-badge--${styleKind}`, `wh-badge--${size}`].join(" ");
  const label: Record<StateKind, string> = {
    police: `⛨ ${t("badge.police")}`,
    gbaby: t("badge.gbaby"),
    lock: `鎖 ${t("badge.locked")}`,
    dead: t("badge.dead"),
    revenge: t("badge.revenge"),
    idiotRevealed: t("badge.idiotRevealed"),
    knife: t("badge.knife"),
    charmed: t("badge.charmed"),
    lover: t("badge.lover"),
    learned: text ? t("badge.learned", { role: text }) : t("badge.learnedShort"),
  };
  return <span className={cls}>{label[kind]}</span>;
}

export function WolfSvg({
  size = 14,
  className,
  style,
  ...props
}: {
  size?: number;
  className?: string;
  style?: React.CSSProperties;
  [key: string]: any;
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      style={style}
      {...props}
    >
      {/* Left Ear */}
      <path d="M4 12V4l5 6" />
      {/* Right Ear */}
      <path d="M20 12V4l-5 6" />
      {/* Forehead & Nose Bridge */}
      <path d="M9 10h6" />
      <path d="M12 10v4" />
      {/* Snout */}
      <path d="M9 10l3 4 3-4" />
      {/* Chin / Jaw */}
      <path d="M4 12l8 9 8-9" />
    </svg>
  );
}
