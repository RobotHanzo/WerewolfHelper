import { useParams } from "react-router-dom";
import { useAuthStore } from "@/stores/authStore";
import { useUiStore } from "@/stores/uiStore";

/**
 * Resolves the active guild from the route (`/server/:guildId/...`) plus the derived role flags.
 * `readOnly` is true for spectators and while a judge is previewing the spectator view.
 */
export function useGuild() {
  const { guildId = "" } = useParams();
  const demo = useAuthStore((s) => s.demo);
  const role = useAuthStore((s) => s.auth?.role ?? "JUDGE");
  const spectatorPreview = useUiStore((s) => s.spectatorPreview);
  const isJudge = role === "JUDGE";
  return { guildId, demo, isJudge, readOnly: !isJudge || spectatorPreview };
}
