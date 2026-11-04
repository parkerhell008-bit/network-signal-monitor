import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";

const CHUNK = 64 * 1024; // 64KB
const MAX_BYTES = 26_214_400; // حداکثر ۲۵ مگابایت

// دانلود: جریان بایت‌های تصادفی برای سنجش پهنای باند واقعی کلاینت
export async function GET(req: NextRequest) {
  const requested = Number(req.nextUrl.searchParams.get("bytes") ?? 10_485_760);
  const total = Number.isFinite(requested)
    ? Math.max(0, Math.min(MAX_BYTES, Math.floor(requested)))
    : 10_485_760;

  // بافر تصادفی یک‌بار ساخته می‌شود و به صورت تکه‌تکه جریان می‌یابد
  const randomChunk = new Uint8Array(CHUNK);
  crypto.getRandomValues(randomChunk);

  let sent = 0;
  const stream = new ReadableStream<Uint8Array>({
    pull(controller) {
      if (sent >= total) {
        controller.close();
        return;
      }
      const remaining = total - sent;
      const size = Math.min(CHUNK, remaining);
      controller.enqueue(size === CHUNK ? randomChunk : randomChunk.subarray(0, size));
      sent += size;
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "application/octet-stream",
      "Content-Length": String(total),
      "Cache-Control": "no-store, no-cache, must-revalidate",
    },
  });
}
