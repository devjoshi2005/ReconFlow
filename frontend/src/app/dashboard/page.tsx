'use client'

import { useState, useEffect } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Activity, AlertTriangle, CheckCircle2, Files } from "lucide-react"

export default function DashboardPage() {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';
    fetch(`${baseUrl}/analytics/summary`, { credentials: 'include' })
      .then(res => res.json())
      .then(d => {
        setData(d)
        setLoading(false)
      })
      .catch(e => {
        console.error("Failed to fetch dashboard analytics", e)
        setLoading(false)
      })
  }, [])

  if (loading) return <div className="flex h-64 items-center justify-center text-zinc-500">Loading Dashboard...</div>

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Dashboard</h2>
        <p className="text-zinc-400">Welcome to ReconFlow. Here is today's snapshot.</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50 hover:bg-zinc-900/60 transition-colors shadow-xl">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Total Processed</CardTitle>
            <Activity className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{data?.totalProcessed?.toLocaleString() || 0}</div>
            <p className="text-xs text-zinc-500">Ledger transactions</p>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50 hover:bg-zinc-900/60 transition-colors shadow-xl">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Success Rate</CardTitle>
            <CheckCircle2 className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-500">{data?.successRateOverall || 0}%</div>
            <p className="text-xs text-zinc-500">Overall Match Rate</p>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50 hover:bg-zinc-900/60 transition-colors shadow-xl">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Open Exceptions</CardTitle>
            <AlertTriangle className="h-4 w-4 text-yellow-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-500">{data?.openExceptions || 0}</div>
            <p className="text-xs text-zinc-500">Requires Attention</p>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Files Queued</CardTitle>
            <Files className="h-4 w-4 text-zinc-400" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{data?.filesQueued || 0}</div>
            <p className="text-xs text-zinc-500">Processing in background...</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
        <Card className="col-span-4 bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader>
            <CardTitle>Reconciliation History</CardTitle>
          </CardHeader>
          <CardContent className="pl-2 flex justify-center items-center h-64 border border-dashed border-zinc-800 rounded-md m-4 mt-0">
             <span className="text-zinc-500">Chart Placeholder (Recharts to be implemented)</span>
          </CardContent>
        </Card>
        <Card className="col-span-3 bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader>
            <CardTitle>Recent Exceptions</CardTitle>
          </CardHeader>
          <CardContent>
             <div className="space-y-4">
               {data?.recentExceptions && data.recentExceptions.length > 0 ? (
                 data.recentExceptions.map((ex: any) => (
                   <div key={ex.id} className="flex items-center justify-between border-b border-zinc-800 pb-2">
                     <div>
                       <p className="text-sm font-medium">{ex.exceptionType}</p>
                       <p className="text-xs text-zinc-400">{ex.matchId}</p>
                     </div>
                     <div className="text-right">
                       <p className="text-sm font-medium text-red-400">-${ex.discrepancyAmount?.toFixed(2) || '0.00'}</p>
                       <p className={`text-xs ${ex.status === 'OPEN' ? 'text-yellow-500' : 'text-zinc-500'}`}>{ex.status}</p>
                     </div>
                   </div>
                 ))
               ) : (
                 <p className="text-sm text-zinc-500">No recent exceptions found.</p>
               )}
             </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
