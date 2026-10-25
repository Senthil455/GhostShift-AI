import { ApiError } from '@/api/client'

export function errorMessage(error: unknown, fallback = 'Something went wrong.'): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error && error.message) return error.message
  if (typeof error === 'string' && error.trim()) return error
  return fallback
}

export function errorDetails(error: unknown): string[] {
  if (!(error instanceof ApiError) || !error.details) return []
  const errors = error.details.errors
  if (Array.isArray(errors)) {
    return errors.filter((item): item is string => typeof item === 'string')
  }
  return []
}
