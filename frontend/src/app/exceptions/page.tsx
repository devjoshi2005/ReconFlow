'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Search, ExternalLink } from 'lucide-react'

// Mock Data representing ExceptionQueue items
const mockExceptions = [
  { id: 'exc-1', matchId: 'E2E-TXN-1790694405569', type: 'AMOUNT_MISMATCH', discrepancy: -42.50, currency: 'INR', status: 'OPEN', gateway: 'RAZORPAY', date: '2023-10-01' },
  { id: 'exc-2', matchId: 'TXN-982112', type: 'MISSING_IN_LEDGER', discrepancy: 500.00, currency: 'INR', status: 'INVESTIGATING', gateway: 'PHONEPE', date: '2023-10-02' },
  { id: 'exc-3', matchId: 'TXN-449102', type: 'AMOUNT_MISMATCH', discrepancy: 10.00, currency: 'INR', status: 'RESOLVED', gateway: 'RAZORPAY', date: '2023-10-02' },
]

export default function ExceptionsPage() {
  const [filterType, setFilterType] = useState<string>('ALL')
  const [search, setSearch] = useState<string>('')

  const filtered = mockExceptions.filter(ex => 
    (filterType === 'ALL' || ex.type === filterType) &&
    ex.matchId.toLowerCase().includes(search.toLowerCase())
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
              <Select value={filterType} onValueChange={setFilterType}>
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
                  <TableCell className="font-medium">{ex.matchId}</TableCell>
                  <TableCell>{ex.gateway}</TableCell>
                  <TableCell>
                    <span className="text-xs font-mono bg-zinc-800 px-2 py-1 rounded">
                      {ex.type}
                    </span>
                  </TableCell>
                  <TableCell className="text-right text-red-400 font-medium">
                    {ex.discrepancy < 0 ? '' : '+'}{ex.discrepancy} {ex.currency}
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
