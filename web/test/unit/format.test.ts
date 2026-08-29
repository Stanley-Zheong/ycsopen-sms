import { describe, expect, it } from 'vitest';
import { formatRatioAsPercent, formatRatioAsPerMille } from '@lib/format';

describe('formatRatioAsPerMille (F-11.9 投诉占比展示格式)', () => {
  it('formats the default threshold 0.003 as "3.00‰"', () => {
    expect(formatRatioAsPerMille(0.003)).toBe('3.00‰');
  });

  it('formats zero ratio as "0.00‰"', () => {
    expect(formatRatioAsPerMille(0)).toBe('0.00‰');
  });

  it('respects custom fraction digits', () => {
    expect(formatRatioAsPerMille(0.0034567, 4)).toBe('3.4567‰');
  });
});

describe('formatRatioAsPercent', () => {
  it('formats 0.003 as "0.300%"', () => {
    expect(formatRatioAsPercent(0.003)).toBe('0.300%');
  });
});
