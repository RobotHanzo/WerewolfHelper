type Size = "sm" | "md" | "lg" | "xl";

const DIM: Record<Size, number> = { sm: 32, md: 40, lg: 56, xl: 96 };

export function Avatar({
  name,
  avatar,
  size = "md",
  dead,
  unassigned,
  speaking,
}: {
  name?: string | null;
  avatar?: string | null;
  size?: Size;
  dead?: boolean;
  unassigned?: boolean;
  speaking?: boolean;
}) {
  const dim = DIM[size];
  const cls = ["wh-avatar", dead ? "wh-avatar--dead" : "", speaking ? "wh-avatar--speaking" : ""].join(" ");
  const initial = unassigned ? "?" : (name?.trim()?.[0] ?? "?");
  return (
    <span className={cls} style={{ width: dim, height: dim, fontSize: dim * 0.4 }}>
      {avatar ? <img src={avatar} alt="" /> : initial}
    </span>
  );
}
