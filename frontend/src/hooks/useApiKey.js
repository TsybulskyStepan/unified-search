import { useState, useCallback } from 'react';

const STORAGE_KEY = 'nevis_api_key';

export function useApiKey() {
  const [apiKey, setApiKeyState] = useState(() => localStorage.getItem(STORAGE_KEY) || '');

  const setApiKey = useCallback((key) => {
    setApiKeyState(key);
    if (key) {
      localStorage.setItem(STORAGE_KEY, key);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  }, []);

  return { apiKey, setApiKey };
}
