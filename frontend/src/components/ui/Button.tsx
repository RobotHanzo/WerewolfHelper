import { useState, type ReactNode } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

interface ButtonProps {
  children: ReactNode;
  onClick?: () => void;
  variant?: Variant;
  size?: Size;
  disabled?: boolean;
  /** Two-step destructive confirm: first click arms, second within ~3s fires. */
  armed?: boolean;
  armedLabel?: string;
  style?: React.CSSProperties;
  title?: string;
}

export function Button({
  children,
  onClick,
  variant = "secondary",
  size = "md",
  disabled,
  armed,
  armedLabel,
  style,
  title,
}: ButtonProps) {
  const [isArmed, setIsArmed] = useState(false);

  const handle = () => {
    if (!armed) return onClick?.();
    if (isArmed) {
      setIsArmed(false);
      onClick?.();
    } else {
      setIsArmed(true);
      setTimeout(() => setIsArmed(false), 3000);
    }
  };

  const classes = ["wh-btn", `wh-btn--${size}`, `wh-btn--${isArmed ? "armed" : variant}`].join(" ");
  return (
    <button type="button" className={classes} onClick={handle} disabled={disabled} style={style} title={title}>
      {isArmed && armedLabel ? armedLabel : children}
    </button>
  );
}
