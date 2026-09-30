'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Label } from '@/components/ui/label'

export default function LoginPage() {
  const [role, setRole] = useState<string>('')
  const router = useRouter()

  const handleLogin = () => {
    if (role) {
      document.cookie = `recon_role=${role}; path=/; max-age=86400`
      router.push('/dashboard')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-950 p-4">
      <Card className="w-full max-w-md bg-zinc-900 border-zinc-800 text-zinc-50">
        <CardHeader className="space-y-1">
          <CardTitle className="text-2xl font-bold tracking-tight">ReconFlow</CardTitle>
          <CardDescription className="text-zinc-400">
            Select a mock role to sign in to your dashboard
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="role" className="text-zinc-200">Role</Label>
            <Select onValueChange={setRole}>
              <SelectTrigger id="role" className="bg-zinc-950 border-zinc-800 text-zinc-100">
                <SelectValue placeholder="Select a role" />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-zinc-800 text-zinc-100">
                <SelectItem value="ADMIN">Admin</SelectItem>
                <SelectItem value="MANAGER">Manager</SelectItem>
                <SelectItem value="ANALYST">Analyst</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <Button 
            className="w-full bg-blue-600 hover:bg-blue-700 text-white" 
            onClick={handleLogin}
            disabled={!role}
          >
            Sign In
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
