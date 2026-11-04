"use client";

import { motion } from "framer-motion";
import { faNum } from "@/lib/persian";

interface SignalGaugeProps {
  /** امتیاز 0..100 */
  score: number;
  /** برچسب فارسی کیفیت */
  label: string;
  /** مقدار dBm تخمینی برای نمایش داخلی گیج (اختیاری) */
  dbmEstimate?: number | null;
  size?: number;
  subtitle?: string;
}

const START_ANGLE = -220;
const SWEEP = 260;

function polar(cx: number, cy: number, r: number, angleDeg: number) {
  const rad = ((angleDeg - 90) * Math.PI) / 180;
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
}

function arcPath(cx: number, cy: number, r: number, a0: number, a1: number) {
  const start = polar(cx, cy, r, a0);
  const end = polar(cx, cy, r, a1);
  const large = a1 - a0 > 180 ? 1 : 0;
  return `M ${start.x.toFixed(2)} ${start.y.toFixed(2)} A ${r} ${r} 0 ${large} 1 ${end.x.toFixed(2)} ${end.y.toFixed(2)}`;
}

/** گیج قوسی ۲۶۰ درجه برای کیفیت سیگنال — هندسهٔ بدون برش */
export function SignalGauge({
  score,
  label,
  dbmEstimate,
  size = 240,
  subtitle,
}: SignalGaugeProps) {
  const cx = size / 2;
  const cy = size / 2 - 10; // مرکز کمی بالاتر تا پایین قوس جا شود
  const r = size / 2 - 26;
  const height = Math.round(size * 0.9);
  const clamped = Math.max(0, Math.min(100, score));
  const angle = START_ANGLE + (clamped / 100) * SWEEP;

  const color =
    clamped >= 80
      ? "#10b981"
      : clamped >= 60
        ? "#84cc16"
        : clamped >= 40
          ? "#eab308"
          : clamped >= 20
            ? "#f97316"
            : clamped > 0
              ? "#ef4444"
              : "#52525b";

  const ticks = Array.from({ length: 14 }, (_, i) => START_ANGLE + (i / 13) * SWEEP);

  return (
    <div className="relative flex items-center justify-center" role="img"
      aria-label={`کیفیت سیگنال: ${label}، امتیاز ${faNum(clamped, 0)} از ۱۰۰`}>
      <svg width={size} height={height} viewBox={`0 0 ${size} ${height}`}>
        {/* تیک‌های پس‌زمینه */}
        {ticks.map((a, i) => {
          const outer = polar(cx, cy, r + 10, a);
          const inner = polar(cx, cy, r + (i % 2 === 0 ? 4 : 7), a);
          return (
            <line
              key={i}
              x1={inner.x}
              y1={inner.y}
              x2={outer.x}
              y2={outer.y}
              stroke="#3f3f46"
              strokeWidth={i % 2 === 0 ? 2 : 1}
              strokeLinecap="round"
            />
          );
        })}

        {/* ریل پس‌زمینه */}
        <path
          d={arcPath(cx, cy, r, START_ANGLE, START_ANGLE + SWEEP)}
          fill="none"
          stroke="#27272a"
          strokeWidth={13}
          strokeLinecap="round"
        />

        {/* کمان مقدار */}
        {clamped > 0 && (
          <motion.path
            fill="none"
            stroke={color}
            strokeWidth={13}
            strokeLinecap="round"
            className="gauge-glow"
            initial={false}
            animate={{ d: arcPath(cx, cy, r, START_ANGLE, angle) }}
            transition={{ type: "spring", stiffness: 60, damping: 18 }}
          />
        )}

        {/* عقربه */}
        <motion.line
          x1={cx}
          y1={cy}
          stroke={color}
          strokeWidth={3}
          strokeLinecap="round"
          initial={false}
          animate={{
            x2: polar(cx, cy, r - 22, angle).x,
            y2: polar(cx, cy, r - 22, angle).y,
          }}
          transition={{ type: "spring", stiffness: 55, damping: 15 }}
        />
        <circle cx={cx} cy={cy} r={6} fill={color} />
        <circle cx={cx} cy={cy} r={2.5} fill="#09090b" />

        {/* اعداد شروع و پایان */}
        <text x={polar(cx, cy, r - 36, START_ANGLE).x} y={polar(cx, cy, r - 30, START_ANGLE).y}
          fill="#71717a" fontSize={10} textAnchor="middle">۰</text>
        <text x={polar(cx, cy, r - 36, START_ANGLE + SWEEP).x} y={polar(cx, cy, r - 30, START_ANGLE + SWEEP).y}
          fill="#71717a" fontSize={10} textAnchor="middle">۱۰۰</text>
      </svg>

      {/* متن مرکزی */}
      <div className="absolute flex flex-col items-center justify-center pointer-events-none"
        style={{ top: "56%", transform: "translateY(-50%)" }}>
        <span className="text-4xl font-extrabold tabular-nums" style={{ color }}>
          {faNum(clamped, 0)}
        </span>
        <span className="text-sm font-semibold mt-0.5" style={{ color }}>
          {label}
        </span>
        {dbmEstimate !== null && dbmEstimate !== undefined && (
          <span className="text-[11px] text-zinc-500 mt-1 tabular-nums">
            ≈ {faNum(dbmEstimate, 0)} dBm تخمینی
          </span>
        )}
        {subtitle && (
          <span className="text-[11px] text-zinc-500 mt-1">{subtitle}</span>
        )}
      </div>
    </div>
  );
}
