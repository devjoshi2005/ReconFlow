'use client'

import { useState, useEffect } from 'react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { BarChart3, TrendingUp, TrendingDown, Clock, AlertTriangle, Layers, Server, Database, HardDrive, Activity } from "lucide-react"
import { 
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
  AreaChart, Area, LineChart, Line, PieChart, Pie, Cell
} from 'recharts'

export default function AnalyticsPage() {
  const [data, setData] = useState<any>(null)
  const [infraData, setInfraData] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [loadingInfra, setLoadingInfra] = useState(true)

  // Use client side cookie check for role (if available), or generic admin flag
  const [isAdmin, setIsAdmin] = useState(false);

  useEffect(() => {
    // Check if user is admin
    const role = document.cookie.split('; ').find(row => row.startsWith('recon_role='))?.split('=')[1];
    setIsAdmin(role === 'ADMIN' || role === 'MANAGER'); // Treating MANAGER as admin for now if role isn't strict

    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

    // Fetch Business Analytics
    fetch(`${baseUrl}/analytics/summary`, { credentials: 'include' })
      .then(res => res.json())
      .then(d => {
        setData(d)
        setLoading(false)
      })
      .catch(e => {
        console.error("Failed to fetch analytics", e)
        setLoading(false)
      })

    // Fetch Infrastructure Analytics
    fetch(`${baseUrl}/system/infrastructure`, { credentials: 'include' })
      .then(res => {
        if (!res.ok) throw new Error('Failed to fetch infrastructure data');
        return res.json();
      })
      .then(d => {
        setInfraData(d)
        setLoadingInfra(false)
      })
      .catch(e => {
        console.error("Failed to fetch infrastructure data", e)
        setInfraData(null)
        setLoadingInfra(false)
      })
  }, [])

  if (loading) {
    return <div className="flex h-64 items-center justify-center text-zinc-500">Loading Analytics...</div>
  }

  // Use real data if available, fallback to mock (or empty if none)
  const volumeData = data?.volumeTimeSeries || []
  const exceptionTimeData = data?.exceptionTimeSeries || []

  // Use real data if available, fallback to mock
  const totalProcessed = data?.totalProcessed || 0
  const successA = data?.successRatePhonePe || 0
  const successB = data?.successRateRazorpay || 0
  const openExceptions = data?.openExceptions || 0
  const avgTime = data?.avgResolutionTimeDays || 0
  const highestSource = data?.highestExceptionSource || 'Unknown'
  
  const typeData = data?.exceptionsByType || [
    { type: 'AMOUNT_MISMATCH', count: 42 },
    { type: 'MISSING_IN_LEDGER', count: 18 }
  ]

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      <div>
        <h2 className="text-3xl font-black tracking-tight bg-gradient-to-r from-white to-zinc-400 bg-clip-text text-transparent">
          Analytics Hub
        </h2>
        <p className="text-zinc-400 mt-1">Deep dive into reconciliation success rates and system health.</p>
      </div>

      <Tabs defaultValue="business" className="w-full">
        <TabsList className="bg-zinc-900/50 border border-zinc-800/50 mb-4 p-1 rounded-xl grid grid-cols-2 max-w-md">
          <TabsTrigger value="business" className="rounded-lg data-[state=active]:bg-emerald-600/20 data-[state=active]:text-emerald-400 transition-all">Business Analytics (PostgreSQL)</TabsTrigger>
          <TabsTrigger value="infrastructure" className="rounded-lg data-[state=active]:bg-blue-600/20 data-[state=active]:text-blue-400 transition-all">System Health (CloudWatch)</TabsTrigger>
        </TabsList>

        <TabsContent value="business" className="space-y-6">
          {/* 6 KPI Cards */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            <Card className="bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 hover:bg-zinc-900/60 transition-colors shadow-2xl">
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Total Processed</CardTitle>
                <Layers className="h-4 w-4 text-blue-500" />
              </CardHeader>
              <CardContent>
                <div className="text-4xl font-black text-white bg-gradient-to-br from-white to-zinc-500 bg-clip-text text-transparent">{totalProcessed.toLocaleString()}</div>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 hover:bg-zinc-900/60 transition-colors shadow-2xl">
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Match Rate (PhonePe)</CardTitle>
                <TrendingUp className="h-4 w-4 text-emerald-500" />
              </CardHeader>
              <CardContent>
                <div className="text-4xl font-black text-emerald-400">{successA}%</div>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50">
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Match Rate (Razorpay)</CardTitle>
                <TrendingUp className="h-4 w-4 text-emerald-500" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold text-emerald-400">{successB}%</div>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50">
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Avg Resolution Time</CardTitle>
                <Clock className="h-4 w-4 text-indigo-400" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold text-white">{avgTime} Days</div>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50">
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Open Exceptions</CardTitle>
                <AlertTriangle className="h-4 w-4 text-amber-500" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold text-amber-500">{openExceptions}</div>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50">
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Highest Exception Source</CardTitle>
                <TrendingDown className="h-4 w-4 text-red-500" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold text-red-400">{highestSource}</div>
              </CardContent>
            </Card>
          </div>

          {/* Charts Row 1 */}
          <div className="grid gap-4 md:grid-cols-2">
            <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50">
              <CardHeader>
                <CardTitle className="text-sm font-medium">Reconciliation Volume (Last 7 Days)</CardTitle>
              </CardHeader>
              <CardContent className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={volumeData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#27272a" />
                    <XAxis dataKey="name" stroke="#71717a" fontSize={12} />
                    <YAxis stroke="#71717a" fontSize={12} />
                    <Tooltip contentStyle={{ backgroundColor: '#18181b', borderColor: '#27272a' }} />
                    <Legend />
                    <Line type="monotone" dataKey="processed" stroke="#8b5cf6" strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="matched" stroke="#10b981" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50">
              <CardHeader>
                <CardTitle className="text-sm font-medium">Exception Type Distribution</CardTitle>
              </CardHeader>
              <CardContent className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={typeData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#27272a" />
                    <XAxis dataKey="type" stroke="#71717a" fontSize={10} />
                    <YAxis stroke="#71717a" fontSize={12} />
                    <Tooltip contentStyle={{ backgroundColor: '#18181b', borderColor: '#27272a' }} />
                    <Bar dataKey="count" fill="#ef4444" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </div>

          {/* Charts Row 2 */}
          <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50">
            <CardHeader>
              <CardTitle className="text-sm font-medium">Exceptions Over Time</CardTitle>
            </CardHeader>
            <CardContent className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={exceptionTimeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#27272a" />
                  <XAxis dataKey="name" stroke="#71717a" fontSize={12} />
                  <YAxis stroke="#71717a" fontSize={12} />
                  <Tooltip contentStyle={{ backgroundColor: '#18181b', borderColor: '#27272a' }} />
                  <Legend />
                  <Area type="monotone" dataKey="OPEN" stackId="1" stroke="#f59e0b" fill="#f59e0b" fillOpacity={0.6} />
                  <Area type="monotone" dataKey="INVESTIGATING" stackId="1" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.6} />
                  <Area type="monotone" dataKey="RESOLVED" stackId="1" stroke="#10b981" fill="#10b981" fillOpacity={0.6} />
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
          <div className="pt-6 border-t border-zinc-800/50 mt-8"></div>
          {/* Gateway Metrics */}
          <Card className="bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 shadow-2xl">
            <CardHeader>
              <CardTitle>Match Rates by Gateway</CardTitle>
              <CardDescription>Comparison of reconciliation success rates across payment processors.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="h-[400px] mt-4">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={[
                    { name: 'Razorpay', rate: data?.successRateRazorpay || 0 },
                    { name: 'PhonePe', rate: data?.successRatePhonePe || 0 },
                    { name: 'UPAY', rate: data?.successRateUpay || 0 }
                  ]}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#27272a" />
                    <XAxis dataKey="name" stroke="#71717a" />
                    <YAxis stroke="#71717a" domain={[0, 100]} />
                    <Tooltip contentStyle={{ backgroundColor: '#18181b', borderColor: '#27272a' }} cursor={{fill: '#27272a', opacity: 0.4}} />
                    <Bar dataKey="rate" fill="#3b82f6" radius={[4, 4, 0, 0]} name="Success Rate (%)" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
          <div className="pt-6 border-t border-zinc-800/50 mt-8"></div>
          {/* Type Metrics */}
          <Card className="bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 shadow-2xl">
            <CardHeader>
              <CardTitle>Exception Distribution</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="h-[400px] mt-4">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={typeData}
                      dataKey="count"
                      nameKey="type"
                      cx="50%"
                      cy="50%"
                      outerRadius={150}
                      fill="#8884d8"
                      label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                    >
                      {typeData.map((entry: any, index: number) => (
                        <Cell key={`cell-${index}`} fill={['#3b82f6', '#ef4444', '#f59e0b', '#10b981', '#8b5cf6'][index % 5]} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ backgroundColor: '#18181b', borderColor: '#27272a' }} />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="infrastructure" className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
            {loadingInfra ? (
              <div className="flex h-64 items-center justify-center text-zinc-500">Loading Infrastructure Data...</div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
                {/* SQS Main Queue */}
                <Card className="bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 shadow-2xl group hover:border-blue-500/50 transition-colors">
                  <CardHeader className="flex flex-row items-center justify-between pb-2">
                    <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">SQS Main Queue</CardTitle>
                    <Server className="h-4 w-4 text-blue-500 group-hover:scale-110 transition-transform" />
                  </CardHeader>
                  <CardContent>
                    <div className="text-4xl font-black text-white">{infraData?.sqsMainDepth || 0}</div>
                    <p className="text-xs text-zinc-500 mt-1">Pending messages</p>
                  </CardContent>
                </Card>

                {/* SQS DLQ */}
                <Card className={`bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 shadow-2xl group transition-colors ${(infraData?.sqsDlqDepth || 0) > 0 ? 'border-red-500/50 hover:border-red-500' : 'hover:border-emerald-500/50'}`}>
                  <CardHeader className="flex flex-row items-center justify-between pb-2">
                    <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">SQS DLQ</CardTitle>
                    <AlertTriangle className={`h-4 w-4 ${(infraData?.sqsDlqDepth || 0) > 0 ? 'text-red-500' : 'text-emerald-500'} group-hover:scale-110 transition-transform`} />
                  </CardHeader>
                  <CardContent>
                    <div className={`text-4xl font-black ${(infraData?.sqsDlqDepth || 0) > 0 ? 'text-red-400' : 'text-emerald-400'}`}>{infraData?.sqsDlqDepth || 0}</div>
                    <p className="text-xs text-zinc-500 mt-1">Failed messages</p>
                  </CardContent>
                </Card>

                {/* S3 Storage */}
                <Card className="bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 shadow-2xl group hover:border-amber-500/50 transition-colors">
                  <CardHeader className="flex flex-row items-center justify-between pb-2">
                    <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">S3 Storage</CardTitle>
                    <HardDrive className="h-4 w-4 text-amber-500 group-hover:scale-110 transition-transform" />
                  </CardHeader>
                  <CardContent>
                    <div className="text-4xl font-black text-white">{( (infraData?.s3TotalSize || 0) / (1024 * 1024) ).toFixed(2)} MB</div>
                    <p className="text-xs text-zinc-500 mt-1">{infraData?.s3FileCount || 0} files total</p>
                  </CardContent>
                </Card>

                {/* API Latency */}
                <Card className="bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 shadow-2xl group hover:border-purple-500/50 transition-colors">
                  <CardHeader className="flex flex-row items-center justify-between pb-2">
                    <CardTitle className="text-xs font-medium text-zinc-400 uppercase tracking-wider">API p95 Latency</CardTitle>
                    <Activity className="h-4 w-4 text-purple-500 group-hover:scale-110 transition-transform" />
                  </CardHeader>
                  <CardContent>
                    <div className="text-4xl font-black text-white">{infraData?.apiLatencyP95 || 0} ms</div>
                    <p className="text-xs text-zinc-500 mt-1">CloudWatch Avg</p>
                  </CardContent>
                </Card>
              </div>
            )}
          </TabsContent>
      </Tabs>
    </div>
  )
}
