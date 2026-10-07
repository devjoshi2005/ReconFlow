'use client'

import { useState, useEffect } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Activity, RefreshCw } from 'lucide-react'

export default function JobsPage() {
  const [files, setFiles] = useState<any[]>([])
  const [loading, setLoading] = useState(true)

  const fetchJobs = () => {
    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';
    fetch(`${baseUrl}/upload`, { credentials: 'include' })
      .then(res => res.json())
      .then(data => {
        setFiles(data)
        setLoading(false)
      })
      .catch(e => {
        console.error("Failed to fetch jobs", e)
        setLoading(false)
      })
  }

  useEffect(() => {
    fetchJobs()

    const apiUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1'
    const eventSource = new EventSource(`${apiUrl}/jobs/stream`, { withCredentials: true })
    
    eventSource.addEventListener('job_event', (event) => {
      console.log('Received job event:', event.data)
      fetchJobs() // Auto refresh when a job finishes
    })

    return () => {
      eventSource.close()
    }
  }, [])

  const getStatusColor = (status: string) => {
    switch(status) {
      case 'QUEUED': return 'bg-blue-500/10 text-blue-400 border-blue-500/20'
      case 'PROCESSING': return 'bg-yellow-500/10 text-yellow-400 border-yellow-500/20'
      case 'COMPLETED': return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
      case 'FAILED': return 'bg-red-500/10 text-red-400 border-red-500/20'
      default: return 'bg-zinc-500/10 text-zinc-400 border-zinc-500/20'
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-3xl font-bold tracking-tight">Job Monitor</h2>
          <p className="text-zinc-400">Track the status of background asynchronous file processing.</p>
        </div>
        <button 
          onClick={fetchJobs}
          className="flex items-center gap-2 px-4 py-2 bg-zinc-900 border border-zinc-800 hover:bg-zinc-800 rounded-lg transition-colors text-sm font-medium"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-blue-400' : 'text-zinc-400'}`} />
          Refresh
        </button>
      </div>

      <Card className="bg-zinc-900/40 backdrop-blur-md border-zinc-800/50 shadow-2xl">
        <CardHeader className="border-b border-zinc-800/50 pb-4">
          <CardTitle className="text-lg flex items-center gap-2 text-zinc-100">
            <Activity className="w-5 h-5 text-blue-400" />
            Uploaded File Pipeline
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <Table>
              <TableHeader className="bg-zinc-950/50">
                <TableRow className="border-zinc-800 hover:bg-transparent">
                  <TableHead className="text-zinc-400">File Name</TableHead>
                  <TableHead className="text-zinc-400">Source Type</TableHead>
                  <TableHead className="text-zinc-400">Context</TableHead>
                  <TableHead className="text-zinc-400">Uploaded At</TableHead>
                  <TableHead className="text-zinc-400">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {files.map((file) => (
                  <TableRow key={file.id} className="border-zinc-800 hover:bg-zinc-800/30 transition-colors">
                    <TableCell className="font-medium text-zinc-200">{file.fileName}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className="bg-zinc-800 text-zinc-300 border-zinc-700">
                        {file.sourceType}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-zinc-400 text-sm">
                      {file.sourceType === 'GATEWAY' ? file.gateway : file.bankName}
                    </TableCell>
                    <TableCell className="text-zinc-400 text-sm">
                      {new Date(file.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className={getStatusColor(file.status)}>
                        {file.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
                {files.length === 0 && !loading && (
                  <TableRow className="border-zinc-800 hover:bg-transparent">
                    <TableCell colSpan={5} className="h-32 text-center text-zinc-500">
                      No files have been uploaded yet.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
