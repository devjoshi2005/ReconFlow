'use client'

import { useState, useEffect } from 'react'
import Link from 'next/link'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Search, ExternalLink } from 'lucide-react'

export default function ExceptionsPage() {
  const [filterType, setFilterType] = useState<string>('ALL')
  const [search, setSearch] = useState<string>('')
  const [exceptions, setExceptions] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';
    fetch(`${baseUrl}/exceptions`, { credentials: 'include' })
      .then(res => res.json())
      .then(data => {
        setExceptions(data)
        setLoading(false)
      })
      .catch(e => {
        console.error("Failed to fetch exceptions", e)
        setLoading(false)
      })
  }, [])

  const filtered = exceptions.filter(ex => 
    (filterType === 'ALL' || ex.exceptionType === filterType) &&
    ex.matchId?.toLowerCase().includes(search.toLowerCase())
  )

  const getStatusColor = (status: string) => {
    switch(status) {
      case 'OPEN': return 'bg-red-500/10 text-red-500 border-red-500/20'
      case 'INVESTIGATING': return 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20'
      case 'RESOLVED': return 'bg-green-500/10 text-green-500 border-green-500/20'
      default: return 'bg-zinc-500/10 text-zinc-500'
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Exception Queue</h2>
          <p className="text-zinc-400">Manage and resolve reconciliation discrepancies.</p>
        </div>
      </div>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
        <CardHeader className="border-b border-zinc-800 pb-4">
          <div className="flex flex-col sm:flex-row gap-4 justify-between items-center">
            <div className="flex flex-1 items-center space-x-2 w-full max-w-sm">
              <div className="relative w-full">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-zinc-500" />
                <Input
                  type="search"
                  placeholder="Search Transaction ID..."
                  className="pl-8 bg-zinc-950 border-zinc-800 text-zinc-100"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </div>
            </div>
            <div className="w-full sm:w-auto">
              <Select value={filterType} onValueChange={(val: any) => setFilterType(val)}>
                <SelectTrigger className="w-[180px] bg-zinc-950 border-zinc-800 text-zinc-100">
                  <SelectValue placeholder="Exception Type" />
                </SelectTrigger>
                <SelectContent className="bg-zinc-900 border-zinc-800 text-zinc-100">
                  <SelectItem value="ALL">All Types</SelectItem>
                  <SelectItem value="AMOUNT_MISMATCH">Amount Mismatch</SelectItem>
                  <SelectItem value="MISSING_IN_LEDGER">Missing In Ledger</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          <Table>
            <TableHeader className="bg-zinc-950/50">
              <TableRow className="border-zinc-800 hover:bg-transparent">
                <TableHead className="text-zinc-400">Timestamp</TableHead>
                <TableHead className="text-zinc-400">Transaction ID</TableHead>
                <TableHead className="text-zinc-400">Gateway</TableHead>
                <TableHead className="text-zinc-400">Type</TableHead>
                <TableHead className="text-zinc-400 text-right">Discrepancy</TableHead>
                <TableHead className="text-zinc-400">Status</TableHead>
                <TableHead className="text-zinc-400 text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((ex) => (
                <TableRow key={ex.id} className="border-zinc-800 hover:bg-zinc-800/50">
                  <TableCell className="text-zinc-400 text-sm">{new Date(ex.createdAt).toLocaleString()}</TableCell>
                  <TableCell className="font-medium">{ex.matchId}</TableCell>
                  <TableCell>{ex.gateway}</TableCell>
                  <TableCell>
                    <span className="text-xs font-mono bg-zinc-800 px-2 py-1 rounded">
                      {ex.exceptionType}
                    </span>
                  </TableCell>
                  <TableCell className="text-right text-red-400 font-medium">
                    {ex.discrepancyAmount < 0 ? '' : '+'}{ex.discrepancyAmount} {ex.currency}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className={getStatusColor(ex.status)}>
                      {ex.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Link href={`/exceptions/${ex.id}/resolve`}>
                      <Button variant="ghost" size="sm" className="text-blue-400 hover:text-blue-300 hover:bg-blue-900/20">
                        Resolve <ExternalLink className="ml-2 w-3 h-3" />
                      </Button>
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
              {filtered.length === 0 && (
                <TableRow className="border-zinc-800 hover:bg-transparent">
                  <TableCell colSpan={6} className="h-24 text-center text-zinc-500">
                    No exceptions found.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
