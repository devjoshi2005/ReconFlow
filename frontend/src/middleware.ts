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
  const token = request.cookies.get('accessToken')?.value

  // Always allow public routes (login, landing)
  if (pathname === '/login' || pathname === '/') {
    if (pathname === '/login' && token) {
      return NextResponse.redirect(new URL('/dashboard', request.url))
    }
    return NextResponse.next()
  }

  // Redirect to login if no token
  if (!token) {
    return NextResponse.redirect(new URL('/login', request.url))
  }

  // Role based checks are now handled by the backend (@PreAuthorize)
  // and the frontend AuthProvider / RouteGuard if needed.
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
