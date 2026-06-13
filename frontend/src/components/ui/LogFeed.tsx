import { useEffect, useRef } from "react";
import type { LogEntry } from "@/types/snapshot";

function clock(ts: number): string {
  return new Date(ts).toTimeString().slice(0, 8);
}

/** Typed game log (info / action / alert) with timestamps; auto-scrolls to the newest entry. */
export function LogFeed({ entries, maxHeight = 260 }: { entries: LogEntry[]; maxHeight?: number }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.scrollTop = 0;
  }, [entries.length]);

  return (
    <div className="wh-log" ref={ref} style={{ maxHeight }}>
      {entries.map((e) => (
        <div key={e.id} className={`wh-log__row wh-log__row--${e.severity}`}>
          <span className="wh-log__time">{clock(e.timestamp)}</span>
          <span>{e.text}</span>
        </div>
      ))}
    </div>
  );
}
