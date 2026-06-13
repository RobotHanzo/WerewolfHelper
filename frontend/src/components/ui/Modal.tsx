import { AnimatePresence, motion } from "framer-motion";
import type { ReactNode } from "react";

/** Overlay modal: backdrop blur + fade, content fade+scale (320ms ease-out). Click outside closes. */
export function Modal({
  open,
  onClose,
  width = 380,
  children,
}: {
  open: boolean;
  onClose: () => void;
  width?: number;
  children: ReactNode;
}) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="wh-modal-backdrop"
          onClick={onClose}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          <motion.div
            className="wh-card"
            onClick={(e) => e.stopPropagation()}
            style={{ width, maxWidth: "100%", maxHeight: "85vh", overflowY: "auto", padding: 24, borderRadius: "var(--r-xl)", boxShadow: "var(--shadow-3)" }}
            initial={{ opacity: 0, scale: 0.96, y: 8 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 8 }}
            transition={{ duration: 0.32, ease: [0.22, 1, 0.36, 1] }}
          >
            {children}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
