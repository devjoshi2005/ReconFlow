'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { 
  LayoutDashboard, 
  Upload, 
  AlertCircle, 
  BarChart3, 
  Settings, 
  Users,
  Activity,
  LogOut
} from 'lucide-react'
import { cn } from '@/lib/utils'

interface SidebarProps {
  role: string
}

export function Sidebar({ role }: SidebarProps) {
  const pathname = usePathname()
  const router = useRouter()

  const handleLogout = () => {
    document.cookie = 'recon_role=; path=/; max-age=0'
    router.push('/login')
  }

  const routes = [
    {
      label: 'Dashboard',
      icon: LayoutDashboard,
      href: '/dashboard',
      roles: ['ADMIN', 'MANAGER', 'ANALYST'],
    },
    {
      label: 'Upload Files',
      icon: Upload,
      href: '/upload',
      roles: ['ADMIN', 'MANAGER', 'ANALYST'],
    },
    {
      label: 'Exceptions',
      icon: AlertCircle,
      href: '/exceptions',
      roles: ['ADMIN', 'MANAGER', 'ANALYST'],
    },
    {
      label: 'Analytics',
      icon: BarChart3,
      href: '/analytics',
      roles: ['ADMIN', 'MANAGER', 'ANALYST'],
    },
    {
      label: 'Job Monitor',
      icon: Activity,
      href: '/jobs',
      roles: ['ADMIN'],
    },
    {
      label: 'Users',
      icon: Users,
      href: '/users',
      roles: ['ADMIN'],
    },
    {
      label: 'Settings',
      icon: Settings,
      href: '/settings',
      roles: ['ADMIN'],
    },
  ]

  const visibleRoutes = routes.filter(route => route.roles.includes(role))

  return (
    <div className="fixed inset-y-0 left-0 w-64 bg-zinc-950 border-r border-zinc-800 flex flex-col z-10">
      <div className="p-6">
        <h1 className="text-2xl font-bold tracking-tight text-blue-500">ReconFlow</h1>
        <div className="mt-2 flex items-center gap-2">
          <span className="text-xs font-medium px-2 py-1 bg-zinc-800 text-zinc-300 rounded-md">
            {role}
          </span>
        </div>
      </div>

      <nav className="flex-1 px-4 space-y-2 mt-4">
        {visibleRoutes.map((route) => (
          <Link
            key={route.href}
            href={route.href}
            className={cn(
              "flex items-center gap-3 px-3 py-2 rounded-md transition-colors text-sm font-medium",
              pathname.startsWith(route.href) 
                ? "bg-blue-600/10 text-blue-500" 
                : "text-zinc-400 hover:text-zinc-50 hover:bg-zinc-900"
            )}
          >
            <route.icon className="w-5 h-5" />
            {route.label}
          </Link>
        ))}
      </nav>

      <div className="p-4 border-t border-zinc-800">
        <button 
          onClick={handleLogout}
          className="flex items-center gap-3 px-3 py-2 w-full rounded-md transition-colors text-sm font-medium text-red-400 hover:bg-zinc-900"
        >
          <LogOut className="w-5 h-5" />
          Logout
        </button>
      </div>
    </div>
  )
}
