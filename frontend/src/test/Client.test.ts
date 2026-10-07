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

  describe('getHearingForCase handling', () => {
    it('returns null on 204 No Content', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 204,
        ok: true,
      } as any);

      const result = await api.getHearingForCase('case-123');
      expect(result).toBeNull();
    });

    it('returns hearing object on 200 OK', async () => {
      const mockHearing = { id: 'h-1', caseId: 'case-123', scheduledTime: '2026-10-10T10:00:00' };
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        ok: true,
        json: async () => mockHearing,
      } as any);

      const result = await api.getHearingForCase('case-123');
      expect(result).toEqual(mockHearing);
    });

    it('returns null on 404 Not Found fallback', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 404,
        ok: false,
        json: async () => ({ message: 'Not found' }),
      } as any);

      const result = await api.getHearingForCase('case-123');
      expect(result).toBeNull();
    });
  });

  describe('recordJudgeLeave', () => {
    it('sends POST request with leave payload', async () => {
      const mockResponse = {
        id: 'leave-1',
        judgeId: 'judge-1',
        startDate: '2026-10-15',
        endDate: '2026-10-17',
        affectedHearingsCount: 2,
      };

      globalThis.fetch = vi.fn().mockResolvedValue({
        status: 200,
        ok: true,
        json: async () => mockResponse,
      } as any);

      const result = await api.recordJudgeLeave('judge-1', {
        startDate: '2026-10-15',
        endDate: '2026-10-17',
        reason: 'Judicial training',
      });

      expect(result).toEqual(mockResponse);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/v1/judges/judge-1/leave',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            startDate: '2026-10-15',
            endDate: '2026-10-17',
            reason: 'Judicial training',
          }),
        })
      );
    });
  });
});

