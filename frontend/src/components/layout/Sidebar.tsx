'use client'

import { useState } from 'react'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { 
  LayoutDashboard, Upload, AlertCircle, BarChart3, Settings, 
  Users, Activity, LogOut, Menu, X 
} from 'lucide-react'
import { cn } from '@/lib/utils'

interface SidebarProps {
  role: string
}

export function Sidebar({ role }: SidebarProps) {
  const pathname = usePathname()
  const router = useRouter()
  const [isOpen, setIsOpen] = useState(false)

  const handleLogout = () => {
    document.cookie = 'recon_role=; path=/; max-age=0'
    router.push('/login')
  }

  const routes = [
    { label: 'Dashboard', icon: LayoutDashboard, href: '/dashboard', roles: ['ADMIN', 'MANAGER', 'ANALYST'] },
    { label: 'Upload Files', icon: Upload, href: '/upload', roles: ['ADMIN', 'MANAGER', 'ANALYST'] },
    { label: 'Exceptions', icon: AlertCircle, href: '/exceptions', roles: ['ADMIN', 'MANAGER', 'ANALYST'] },
    { label: 'Analytics', icon: BarChart3, href: '/analytics', roles: ['ADMIN', 'MANAGER', 'ANALYST'] },
    { label: 'Job Monitor', icon: Activity, href: '/jobs', roles: ['ADMIN'] },
    { label: 'Users', icon: Users, href: '/users', roles: ['ADMIN'] },
    { label: 'Settings', icon: Settings, href: '/settings', roles: ['ADMIN'] },
  ]

  const visibleRoutes = routes.filter(route => route.roles.includes(role))

  return (
    <>
      {/* Mobile Toggle */}
      <button 
        onClick={() => setIsOpen(!isOpen)} 
        className="md:hidden fixed top-4 right-4 z-50 p-2 bg-zinc-900/80 backdrop-blur-md rounded-md text-zinc-400 hover:text-white transition-all"
      >
        {isOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
      </button>

      {/* Sidebar Overlay for Mobile */}
      {isOpen && (
        <div 
          className="fixed inset-0 bg-black/50 backdrop-blur-sm z-40 md:hidden" 
          onClick={() => setIsOpen(false)}
        />
      )}

      {/* Sidebar Content */}
      <div className={cn(
        "fixed inset-y-0 left-0 w-64 bg-zinc-950/80 backdrop-blur-xl border-r border-zinc-800/50 flex flex-col z-50 transition-transform duration-300 ease-in-out md:translate-x-0",
        isOpen ? "translate-x-0" : "-translate-x-full"
      )}>
        <div className="p-6">
          <h1 className="text-2xl font-black tracking-tighter bg-gradient-to-br from-blue-400 to-indigo-600 bg-clip-text text-transparent">ReconFlow</h1>
          <div className="mt-2 flex items-center gap-2">
            <span className="text-[10px] font-bold tracking-wider px-2 py-0.5 bg-blue-500/10 border border-blue-500/20 text-blue-400 rounded-full shadow-[0_0_10px_rgba(59,130,246,0.1)]">
              {role}
            </span>
          </div>
        </div>

        <nav className="flex-1 px-4 space-y-1.5 mt-4">
          {visibleRoutes.map((route) => {
            const isActive = pathname.startsWith(route.href)
            return (
              <Link
                key={route.href}
                href={route.href}
                onClick={() => setIsOpen(false)}
                className={cn(
                  "group flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 text-sm font-medium relative overflow-hidden",
                  isActive 
                    ? "text-white bg-blue-600/10 border border-blue-500/20 shadow-inner" 
                    : "text-zinc-400 hover:text-zinc-100 hover:bg-zinc-800/40"
                )}
              >
                {isActive && (
                  <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 bg-blue-500 rounded-r-full shadow-[0_0_8px_rgba(59,130,246,0.8)]" />
                )}
                <route.icon className={cn("w-5 h-5 transition-transform duration-200", isActive ? "text-blue-400" : "group-hover:scale-110 group-hover:text-zinc-300")} />
                {route.label}
              </Link>
            )
          })}
        </nav>

        <div className="p-4 border-t border-zinc-800/50">
          <button 
            onClick={handleLogout}
            className="flex items-center gap-3 px-3 py-2.5 w-full rounded-xl transition-all duration-200 text-sm font-medium text-red-400 hover:bg-red-950/30 hover:border-red-900/50 border border-transparent"
          >
            <LogOut className="w-5 h-5" />
            Logout
          </button>
        </div>
      </div>
    </>
  )
}
