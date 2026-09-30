import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Activity, AlertTriangle, CheckCircle2, Files } from "lucide-react"

export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Dashboard</h2>
        <p className="text-zinc-400">Welcome to ReconFlow. Here is today's snapshot.</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Total Processed</CardTitle>
            <Activity className="h-4 w-4 text-blue-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">14,231</div>
            <p className="text-xs text-zinc-500">+20% from yesterday</p>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Successfully Reconciled</CardTitle>
            <CheckCircle2 className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-500">14,102</div>
            <p className="text-xs text-zinc-500">99.1% Success Rate</p>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Open Exceptions</CardTitle>
            <AlertTriangle className="h-4 w-4 text-yellow-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-yellow-500">129</div>
            <p className="text-xs text-zinc-500">Requires Attention</p>
          </CardContent>
        </Card>
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-zinc-400">Files Queued</CardTitle>
            <Files className="h-4 w-4 text-zinc-400" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">2</div>
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
               {[1,2,3,4,5].map(i => (
                 <div key={i} className="flex items-center justify-between border-b border-zinc-800 pb-2">
                   <div>
                     <p className="text-sm font-medium">AMOUNT_MISMATCH</p>
                     <p className="text-xs text-zinc-400">TXN-839201</p>
                   </div>
                   <div className="text-right">
                     <p className="text-sm font-medium text-red-400">-$42.00</p>
                     <p className="text-xs text-yellow-500">Investigating</p>
                   </div>
                 </div>
               ))}
             </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
