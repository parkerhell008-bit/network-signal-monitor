"use client";

import { Card, CardContent } from "@/components/ui/card";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

interface StatCardProps {
  icon: LucideIcon;
  title: string;
  value: ReactNode;
  unit?: string;
  hint?: string;
  iconColor?: string;
}

/** کارت آمار کوچک با آیکن */
export function StatCard({ icon: Icon, title, value, unit, hint, iconColor = "#10b981" }: StatCardProps) {
  return (
    <Card className="bg-zinc-900/70 border-zinc-800 hover:border-zinc-700 transition-colors">
      <CardContent className="p-4">
        <div className="flex items-center gap-2 text-zinc-400 text-xs font-medium">
          <Icon size={15} style={{ color: iconColor }} aria-hidden />
          <span>{title}</span>
        </div>
        <div className="mt-2 flex items-baseline gap-1">
          <span className="text-2xl font-bold text-zinc-100 tabular-nums leading-none">{value}</span>
          {unit && <span className="text-xs text-zinc-500 font-medium">{unit}</span>}
        </div>
        {hint && <p className="mt-1.5 text-[11px] text-zinc-500 leading-relaxed">{hint}</p>}
      </CardContent>
    </Card>
  );
}
