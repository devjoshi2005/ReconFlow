'use client'

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { BarChart3, TrendingUp, TrendingDown, PieChart } from "lucide-react"

export default function AnalyticsPage() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Reconciliation Analytics</h2>
        <p className="text-zinc-400">Deep dive into reconciliation success rates and exception distribution.</p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Monthly Success Rate</CardTitle>
            <TrendingUp className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-500">99.4%</div>
            <p className="text-xs text-zinc-500">+0.2% from last month</p>
          </CardContent>
        </Card>
        
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Avg Resolution Time</CardTitle>
            <BarChart3 className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">1.2 Days</div>
            <p className="text-xs text-zinc-500">-0.4 days from last month</p>
          </CardContent>
        </Card>

        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Highest Exception Source</CardTitle>
            <TrendingDown className="h-4 w-4 text-red-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">PhonePe</div>
            <p className="text-xs text-zinc-500">65% of all AMOUNT_MISMATCH</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader>
            <CardTitle>Exception Type Distribution</CardTitle>
            <CardDescription className="text-zinc-400">Breakdown of discrepancies by category.</CardDescription>
          </CardHeader>
          <CardContent className="h-72 flex flex-col items-center justify-center border border-dashed border-zinc-800 rounded-md">
             <PieChart className="w-12 h-12 text-zinc-700 mb-2" />
             <span className="text-zinc-500">Recharts integration pending</span>
          </CardContent>
        </Card>

        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader>
            <CardTitle>Reconciliation Volume (Last 7 Days)</CardTitle>
            <CardDescription className="text-zinc-400">Daily processed vs reconciled.</CardDescription>
          </CardHeader>
          <CardContent className="h-72 flex flex-col items-center justify-center border border-dashed border-zinc-800 rounded-md">
             <BarChart3 className="w-12 h-12 text-zinc-700 mb-2" />
             <span className="text-zinc-500">Recharts integration pending</span>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
