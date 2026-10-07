import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api } from '../api/client';

describe('ApiClient In-Memory Authentication', () => {
  beforeEach(() => {
    api.clearCredentials();
  });

  it('manages credentials in-memory without accessing localStorage', () => {
    expect(api.hasCredentials()).toBe(false);

    api.setCredentials('registrar', 'registrar123');
    expect(api.hasCredentials()).toBe(true);

    api.clearCredentials();
    expect(api.hasCredentials()).toBe(false);
  });

  it('triggers onUnauthorized callback on 401 response and clears credentials', async () => {
    const unauthorizedCallback = vi.fn();
    api.onUnauthorized(unauthorizedCallback);
    api.setCredentials('invalidUser', 'wrongPass');

    // Mock global fetch to return 401
    globalThis.fetch = vi.fn().mockResolvedValue({
      status: 401,
      ok: false,
      json: async () => ({ message: 'Unauthorized' }),
    } as any);

    await expect(api.getMe()).rejects.toThrow('Authentication required');
    expect(unauthorizedCallback).toHaveBeenCalledTimes(1);
    expect(api.hasCredentials()).toBe(false);
  });
});
