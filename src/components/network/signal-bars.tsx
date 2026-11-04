"use client";

import { motion } from "framer-motion";

interface SignalBarsProps {
  /** تعداد نوارهای روشن 0..5 */
  bars: number;
  color?: string;
  size?: "sm" | "md" | "lg";
}

const heights = { sm: [8, 12, 16, 20, 24], md: [10, 15, 20, 25, 30], lg: [12, 18, 24, 30, 36] };

/** نمایشگر آنتن ۵ نواره */
export function SignalBars({ bars, color = "#10b981", size = "md" }: SignalBarsProps) {
  const hs = heights[size];
  const width = { sm: 4, md: 5, lg: 7 }[size];

  return (
    <div className="flex items-end gap-[3px]" aria-label={`${bars} از ۵ نوار آنتن`}>
      {hs.map((h, i) => {
        const active = i < bars;
        return (
          <motion.span
            key={i}
            className="rounded-sm"
            style={{ width, height: h }}
            initial={false}
            animate={{
              backgroundColor: active ? color : "#3f3f46",
              opacity: active ? 1 : 0.6,
            }}
            transition={{ duration: 0.25, delay: active ? i * 0.05 : 0 }}
          />
        );
      })}
    </div>
  );
}
