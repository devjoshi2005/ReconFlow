import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

// Route access definitions
const roleAccess = {
  ADMIN: ['/dashboard', '/upload', '/exceptions', '/analytics', '/jobs', '/users', '/settings', '/profile'],
  MANAGER: ['/dashboard', '/upload', '/exceptions', '/analytics', '/profile'],
  ANALYST: ['/dashboard', '/upload', '/exceptions', '/analytics', '/profile'], // Note: /analytics/runs and /analytics/settlements are blocked below
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl
  const role = request.cookies.get('recon_role')?.value?.toUpperCase()

  // Always allow public routes (login)
  if (pathname === '/login') {
    if (role) {
      return NextResponse.redirect(new URL('/dashboard', request.url))
    }
    return NextResponse.next()
  }

  // Redirect to login if no role
  if (!role) {
    return NextResponse.redirect(new URL('/login', request.url))
  }

  // Check specific sub-route restrictions
  if (role === 'ANALYST') {
    if (pathname.startsWith('/analytics/runs') || pathname.startsWith('/analytics/settlements')) {
      return NextResponse.redirect(new URL('/dashboard', request.url))
    }
  }

  if (role !== 'ADMIN') {
    if (pathname.startsWith('/jobs') || pathname.startsWith('/users') || pathname.startsWith('/settings')) {
      return NextResponse.redirect(new URL('/dashboard', request.url))
    }
  }

  // Base layout protection (API and static files are ignored via matcher)
  return NextResponse.next()
}

export const config = {
  matcher: [
    /*
     * Match all request paths except for the ones starting with:
     * - api (API routes)
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico (favicon file)
     */
    '/((?!api|_next/static|_next/image|favicon.ico).*)',
  ],
}
