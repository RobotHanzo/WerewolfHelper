import { useEffect, useState } from "react";

/**
 * Subscribes to a CSS media query and re-renders on match changes.
 * Used to switch roster column counts at the same breakpoints the
 * responsive shell CSS uses (see `components/ui/ui.css`).
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() =>
    typeof window !== "undefined" ? window.matchMedia(query).matches : false,
  );

  useEffect(() => {
    const mql = window.matchMedia(query);
    const onChange = () => setMatches(mql.matches);
    onChange();
    mql.addEventListener("change", onChange);
    return () => mql.removeEventListener("change", onChange);
  }, [query]);

  return matches;
}
