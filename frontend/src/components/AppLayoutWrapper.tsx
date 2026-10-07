"use client";

import React from "react";
import { usePathname } from "next/navigation";
import { Sidebar } from "@/components/layout/Sidebar";
import { useAuth } from "@/components/AuthProvider";

export const AppLayoutWrapper = ({ children }: { children: React.ReactNode }) => {
  const pathname = usePathname();
  const { user } = useAuth();
  
  const isPublicRoute = pathname === "/" || pathname === "/login";

  if (isPublicRoute) {
    return <main className="flex-1 w-full z-10 relative">{children}</main>;
  }

  return (
    <>
      <Sidebar role={user?.role || "UNKNOWN"} />
      <div className="flex-1 flex flex-col md:ml-64 min-h-screen relative z-10">
        <div className="md:hidden h-16 w-full flex items-center px-4 bg-zinc-950/80 backdrop-blur-md border-b border-zinc-800/50 sticky top-0 z-30">
          <h1 className="text-xl font-black tracking-tighter bg-gradient-to-br from-blue-400 to-indigo-600 bg-clip-text text-transparent">ReconFlow</h1>
        </div>
        <main className="flex-1 p-4 md:p-8 overflow-y-auto">
          {children}
        </main>
      </div>
    </>
  );
};
