'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Label } from '@/components/ui/label'
import { AlertCircle, CheckCircle2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'

export default function ResolveExceptionPage({ params }: { params: { id: string } }) {
  const router = useRouter()
  // Mock data fetching based on ID
  const ex = { id: params.id, matchId: 'TXN-982112', type: 'MISSING_IN_LEDGER', discrepancy: 500.00, currency: 'INR', status: 'OPEN', gateway: 'PHONEPE' }

  const [resolutionType, setResolutionType] = useState<string>('')
  const [notes, setNotes] = useState<string>('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = (action: 'INVESTIGATE' | 'RESOLVE' | 'APPROVE' | 'DISMISS') => {
    setSubmitting(true)
    // Mock API call
    setTimeout(() => {
      setSubmitting(false)
      router.push('/exceptions')
    }, 1000)
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Resolve Exception</h2>
        <p className="text-zinc-400">Exception ID: {params.id}</p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
          <CardHeader>
            <CardTitle>Discrepancy Details</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <Label className="text-zinc-500">Transaction ID</Label>
              <p className="font-mono">{ex.matchId}</p>
            </div>
            <div>
              <Label className="text-zinc-500">Exception Type</Label>
              <p><Badge variant="outline" className="bg-red-500/10 text-red-500 border-red-500/20">{ex.type}</Badge></p>
            </div>
            <div>
              <Label className="text-zinc-500">Amount Variance</Label>
              <p className="text-xl font-bold text-red-400">+{ex.discrepancy} {ex.currency}</p>
            </div>
            <div>
              <Label className="text-zinc-500">Gateway</Label>
              <p>{ex.gateway}</p>
            </div>
          </CardContent>
        </Card>

        <Card className="bg-zinc-900 border-zinc-800 text-zinc-50 flex flex-col justify-between">
          <CardHeader>
            <CardTitle>Maker-Checker Actions</CardTitle>
            <CardDescription className="text-zinc-400">
              Provide a resolution and notes for approval.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Resolution Action</Label>
              <Select value={resolutionType} onValueChange={(val: any) => setResolutionType(val)}>
                <SelectTrigger className="bg-zinc-950 border-zinc-800 text-zinc-100">
                  <SelectValue placeholder="Select Action" />
                </SelectTrigger>
                <SelectContent className="bg-zinc-900 border-zinc-800 text-zinc-100">
                  <SelectItem value="FORCE_MATCH">Force Match (Write-off)</SelectItem>
                  <SelectItem value="REFUND">Initiate Refund</SelectItem>
                  <SelectItem value="ESCALATE">Escalate to Dev</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Resolution Notes</Label>
              <Textarea 
                placeholder="Explain why this action is being taken..."
                className="bg-zinc-950 border-zinc-800 text-zinc-100 min-h-[100px]"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
          </CardContent>
          <CardFooter className="flex flex-col gap-2">
            <Button 
              className="w-full bg-blue-600 hover:bg-blue-700 text-white"
              disabled={submitting || !resolutionType || !notes}
              onClick={() => handleSubmit('RESOLVE')}
            >
              Submit for Approval
            </Button>
            <Button 
              variant="outline"
              className="w-full border-zinc-700 text-zinc-300 hover:bg-zinc-800 hover:text-white"
              disabled={submitting}
              onClick={() => handleSubmit('INVESTIGATE')}
            >
              Mark as Investigating
            </Button>
          </CardFooter>
        </Card>
      </div>
    </div>
  )
}
