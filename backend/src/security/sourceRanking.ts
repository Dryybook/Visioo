export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN';

export interface RankableSource {
  id: string;
  type: 'HLS' | 'DASH' | 'MP4' | 'WEBM';
  quality: number | null;
  language: string | null;
  priority: number;
  authorized: boolean;
  health: HealthStatus;
  latencyMs: number | null;
}

export interface RankPreferences {
  language?: string;
  preferredQuality?: number;
}

const HEALTH_ORDER: Record<HealthStatus, number> = { HEALTHY: 0, UNKNOWN: 1, DEGRADED: 2, DOWN: 3 };

/**
 * Order from the product spec: authorization, availability, language, quality,
 * adaptive stream, latency, provider priority. Unauthorized and DOWN sources are dropped.
 */
export function rankSources<T extends RankableSource>(sources: T[], prefs: RankPreferences = {}): T[] {
  const target = prefs.preferredQuality ?? 1080;
  const qualityDistance = (s: T) => {
    const q = s.quality ?? 0;
    // Prefer the highest quality not above the target, then the closest above it.
    return q <= target ? target - q : (q - target) * 2 + 10_000;
  };
  return sources
    .filter((s) => s.authorized && s.health !== 'DOWN')
    .sort((a, b) => {
      const keys = (s: T) => [
        HEALTH_ORDER[s.health],
        prefs.language && s.language === prefs.language ? 0 : 1,
        qualityDistance(s),
        s.type === 'HLS' || s.type === 'DASH' ? 0 : 1,
        s.latencyMs ?? Number.MAX_SAFE_INTEGER,
        -s.priority,
      ];
      const ka = keys(a);
      const kb = keys(b);
      for (let i = 0; i < ka.length; i++) {
        const d = (ka[i] as number) - (kb[i] as number);
        if (d !== 0) return d;
      }
      return 0;
    });
}
