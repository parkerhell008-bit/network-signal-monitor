"use client";

import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from "recharts";
import { faNum, faDateTime } from "@/lib/persian";

export interface ChartPoint {
  id: string;
  downloadMbps: number;
  uploadMbps: number;
  pingMs: number;
  createdAt: string;
}

/** نمودار روند تاریخچهٔ تست‌های سرعت (دانلود/آپلود/پینگ) */
export function HistoryChart({ data }: { data: ChartPoint[] }) {
  const points = data.map((d) => ({
    ...d,
    name: faDateTime(d.createdAt),
    dl: Math.round(d.downloadMbps * 10) / 10,
    ul: Math.round(d.uploadMbps * 10) / 10,
    ping: Math.round(d.pingMs),
  }));

  return (
    <div dir="ltr" className="h-64 w-full select-none" aria-label="نمودار روند تست‌های سرعت">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="#27272a" strokeDasharray="3 3" />
          <XAxis
            dataKey="name"
            tick={{ fill: "#71717a", fontSize: 10, fontFamily: "Vazirmatn" }}
            stroke="#3f3f46"
            interval="preserveStartEnd"
            minTickGap={28}
          />
          <YAxis
            yAxisId="speed"
            tick={{ fill: "#71717a", fontSize: 10 }}
            stroke="#3f3f46"
            width={42}
            tickFormatter={(v: number) => String(v)}
          />
          <YAxis
            yAxisId="ping"
            orientation="right"
            tick={{ fill: "#a1a1aa", fontSize: 10 }}
            stroke="#3f3f46"
            width={36}
          />
          <Tooltip
            contentStyle={{
              background: "#18181b",
              border: "1px solid #3f3f46",
              borderRadius: 10,
              fontFamily: "Vazirmatn",
              fontSize: 12,
              direction: "rtl",
            }}
            labelStyle={{ color: "#a1a1aa" }}
            formatter={(value: number | string, name: string) => [
              `${faNum(Number(value), 1)} ${name === "ping" ? "ms" : "Mbps"}`,
              name === "dl" ? "دانلود" : name === "ul" ? "آپلود" : "پینگ",
            ]}
          />
          <Legend
            formatter={(value: string) => (
              <span style={{ color: "#a1a1aa", fontSize: 11 }}>
                {value === "dl" ? "دانلود" : value === "ul" ? "آپلود" : "پینگ"}
              </span>
            )}
          />
          <Line yAxisId="speed" type="monotone" dataKey="dl" stroke="#10b981" strokeWidth={2} dot={{ r: 3, fill: "#10b981" }} activeDot={{ r: 5 }} />
          <Line yAxisId="speed" type="monotone" dataKey="ul" stroke="#a3e635" strokeWidth={2} dot={{ r: 3, fill: "#a3e635" }} activeDot={{ r: 5 }} />
          <Line yAxisId="ping" type="monotone" dataKey="ping" stroke="#eab308" strokeWidth={1.5} strokeDasharray="5 3" dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
