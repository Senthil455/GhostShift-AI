import { describe, expect, it } from 'vitest'
import {
  bytes,
  categoryDefaultFolder,
  categoryLabel,
  duration,
  humanDate,
  humanStatus,
  isActiveRun,
  minutes,
  percent,
  plural,
  relativeTime,
  riskTone,
  shortDate,
  titleCase,
} from './format'

describe('format helpers', () => {
  it('passes through unparseable dates so raw API values stay visible', () => {
    expect(humanDate('not-a-date')).toBe('not-a-date')
    expect(shortDate('not-a-date')).toBe('not-a-date')
    expect(relativeTime('not-a-date')).toBe('not-a-date')
  })

  it('renders empty dates with an em dash', () => {
    expect(humanDate()).toBe('—')
    expect(shortDate(null)).toBe('—')
    expect(relativeTime(undefined)).toBe('—')
  })

  it('formats bytes using binary units', () => {
    expect(bytes(null)).toBe('—')
    expect(bytes(0)).toBe('0 B')
    expect(bytes(1023)).toBe('1023 B')
    expect(bytes(1536)).toBe('1.5 KB')
    expect(bytes(10 * 1024 * 1024)).toBe('10 MB')
    expect(bytes(1024 * 1024 * 1024)).toBe('1.0 GB')
  })

  it('formats durations for milliseconds, seconds, and minutes', () => {
    expect(duration(null)).toBe('—')
    expect(duration(999)).toBe('999 ms')
    expect(duration(1500)).toBe('2s')
    expect(duration(125_000)).toBe('2m 5s')
  })

  it('turns API enums into readable labels', () => {
    expect(humanStatus('WAITING_FOR_APPROVAL')).toBe('Waiting For Approval')
    expect(humanStatus(null)).toBe('Unknown')
    expect(titleCase('study_materials')).toBe('Study Materials')
    expect(categoryLabel('DEVELOPMENT_LOGS')).toBe('Development logs')
    expect(categoryLabel('CUSTOM_VALUE')).toBe('Custom Value')
    expect(categoryLabel(null)).toBe('Other')
  })

  it('mirrors the backend default folder names', () => {
    expect(categoryDefaultFolder('DEVELOPMENT_LOGS')).toBe('development-logs')
    expect(categoryDefaultFolder('SOURCE_CODE')).toBe('source-code')
    expect(categoryDefaultFolder('UNKNOWN')).toBe('other')
  })

  it('pluralizes counts for interface copy', () => {
    expect(plural(1, 'step')).toBe('step')
    expect(plural(2, 'step')).toBe('steps')
    expect(plural(1, 'permission')).toBe('permission')
    expect(plural(0, 'file')).toBe('files')
    expect(plural(1, 'directory', 'directories')).toBe('directory')
    expect(plural(3, 'directory', 'directories')).toBe('directories')
  })

  it('formats minutes and clamped percentages', () => {
    expect(minutes(0.4)).toBe('<1 min')
    expect(minutes(42)).toBe('42 min')
    expect(minutes(125)).toBe('2h 5m')
    expect(minutes(120)).toBe('2h')
    expect(percent(1, 4)).toBe(25)
    expect(percent(4, 0)).toBe(0)
    expect(percent(12, 4)).toBe(100)
    expect(percent(-2, 4)).toBe(0)
  })

  it('recognizes only non-terminal run states as active', () => {
    expect(isActiveRun('QUEUED')).toBe(true)
    expect(isActiveRun('RUNNING')).toBe(true)
    expect(isActiveRun('WAITING_FOR_APPROVAL')).toBe(true)
    expect(isActiveRun('COMPLETED')).toBe(false)
    expect(isActiveRun(undefined)).toBe(false)
  })

  it('maps risk levels to interface tones', () => {
    expect(riskTone('HIGH')).toBe('danger')
    expect(riskTone('moderate')).toBe('warning')
    expect(riskTone('LOW')).toBe('success')
    expect(riskTone('UNKNOWN')).toBe('neutral')
    expect(riskTone()).toBe('neutral')
  })
})
