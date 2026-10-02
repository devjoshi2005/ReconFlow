import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Users } from 'lucide-react'

export default function UsersPage() {
  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">User Management</h2>
        <p className="text-zinc-400">Manage analysts, managers, and system administrators.</p>
      </div>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-50 flex flex-col items-center justify-center min-h-[400px]">
        <CardHeader className="text-center">
          <div className="mx-auto bg-blue-500/10 p-4 rounded-full border border-blue-500/20 mb-4 inline-flex">
            <Users className="w-10 h-10 text-blue-400" />
          </div>
          <CardTitle className="text-2xl">Coming Soon</CardTitle>
          <CardDescription className="text-zinc-400 max-w-sm mx-auto mt-2">
            The User Management module is currently under development. Soon, you will be able to manage role-based access control directly from this page.
          </CardDescription>
        </CardHeader>
      </Card>
    </div>
  )
}
