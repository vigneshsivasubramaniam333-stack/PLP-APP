import { useEffect, useState } from 'react';
import { portalApi } from '@plp/shared';

export function useAnchorEarlyPayEnabled(): boolean {
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    let cancelled = false;
    portalApi
      .earlyPayEnabled()
      .then((res) => {
        if (!cancelled) setEnabled(Boolean(res.data?.enabled));
      })
      .catch(() => {
        if (!cancelled) setEnabled(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return enabled;
}
