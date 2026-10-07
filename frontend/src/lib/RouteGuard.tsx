"use client";

import React from "react";
import { useAuth } from "@/components/AuthProvider";
import { usePathname } from "next/navigation";

export const RouteGuard = ({ children }: { children: React.ReactNode }) => {
  const { user, loading } = useAuth();
  const pathname = usePathname();

  if (pathname === "/login" || pathname === "/") {
    return <>{children}</>;
  }

  if (loading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-[#09090b]">
        <div className="flex flex-col items-center gap-4">
          <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
          <p className="text-sm font-medium text-zinc-400">Verifying session...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return null; // AuthProvider handles the redirect to /login
  }

  // Role-based route access definitions
  const routePermissions: Record<string, string[]> = {
    '/dashboard': ['ADMIN', 'MANAGER', 'ANALYST'],
    '/upload': ['ADMIN', 'MANAGER', 'ANALYST'],
    '/exceptions': ['ADMIN', 'MANAGER', 'ANALYST'],
    '/jobs': ['ADMIN', 'MANAGER'],
    '/analytics': ['ADMIN', 'MANAGER'],
    '/users': ['ADMIN'],
    '/settings': ['ADMIN'],
  };

  // Check if current path requires specific roles
  let hasAccess = true;
  const normalizedRole = user.role.startsWith('ROLE_') ? user.role.substring(5) : user.role;
  
  for (const [route, allowedRoles] of Object.entries(routePermissions)) {
    if (pathname.startsWith(route)) {
      if (!allowedRoles.includes(normalizedRole)) {
        hasAccess = false;
      }
      break;
    }
  }

  if (!hasAccess) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-[#09090b] p-4">
        <div className="max-w-md w-full bg-zinc-950/80 backdrop-blur-xl border border-red-900/50 rounded-2xl p-8 text-center shadow-2xl">
          <div className="w-16 h-16 bg-red-500/10 rounded-full flex items-center justify-center mx-auto mb-6">
            <svg xmlns="http://www.w3.org/2000/svg" className="h-8 w-8 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-white mb-2">403 Forbidden</h1>
          <p className="text-zinc-400 mb-6">
            You do not have the required permissions ({user.role}) to access this page. Please contact your administrator if you believe this is a mistake.
          </p>
          <button 
            onClick={() => window.history.back()}
            className="px-6 py-2.5 bg-white/5 border border-white/10 rounded-lg text-white font-medium hover:bg-white/10 transition-colors"
          >
            Go Back
          </button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
};
