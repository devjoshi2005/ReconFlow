'use client'

import { useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { UploadCloud, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react'

export default function UploadPage() {
  const [file, setFile] = useState<File | null>(null)
  const [gateway, setGateway] = useState<string>('RAZORPAY')
  const [status, setStatus] = useState<'idle' | 'uploading' | 'success' | 'error'>('idle')
  const [message, setMessage] = useState<string>('')

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setFile(e.target.files[0])
      setStatus('idle')
      setMessage('')
    }
  }

  const handleUpload = async () => {
    if (!file) return

    setStatus('uploading')
    const formData = new FormData()
    formData.append('file', file)
    formData.append('gateway', gateway)
    formData.append('sourceType', 'GATEWAY')

    try {
      const response = await fetch('http://localhost:8080/api/v1/upload', {
        method: 'POST',
        body: formData,
      })

      if (response.ok) {
        setStatus('success')
        setMessage('File successfully uploaded and queued for processing.')
        setFile(null)
      } else {
        const err = await response.text()
        setStatus('error')
        setMessage(`Upload failed: ${err}`)
      }
    } catch (error) {
      setStatus('error')
      setMessage('Network error occurred while uploading.')
    }
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">Upload Settlement File</h2>
        <p className="text-zinc-400">Upload CSV files from Payment Gateways for reconciliation.</p>
      </div>

      <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
        <CardHeader>
          <CardTitle>File Details</CardTitle>
          <CardDescription className="text-zinc-400">
            Ensure the CSV matches the standard format for the selected gateway.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <Label>Gateway Source</Label>
            <Select value={gateway} onValueChange={setGateway}>
              <SelectTrigger className="bg-zinc-950 border-zinc-800 text-zinc-100">
                <SelectValue placeholder="Select Gateway" />
              </SelectTrigger>
              <SelectContent className="bg-zinc-900 border-zinc-800 text-zinc-100">
                <SelectItem value="RAZORPAY">Razorpay</SelectItem>
                <SelectItem value="PHONEPE">PhonePe</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label>CSV File</Label>
            <div className="flex items-center justify-center w-full">
              <label htmlFor="dropzone-file" className="flex flex-col items-center justify-center w-full h-48 border-2 border-zinc-800 border-dashed rounded-lg cursor-pointer bg-zinc-950/50 hover:bg-zinc-800/50 transition-colors">
                <div className="flex flex-col items-center justify-center pt-5 pb-6">
                  <UploadCloud className="w-10 h-10 mb-3 text-zinc-400" />
                  <p className="mb-2 text-sm text-zinc-400">
                    <span className="font-semibold text-blue-500">Click to upload</span> or drag and drop
                  </p>
                  <p className="text-xs text-zinc-500">CSV or TXT (MAX. 50MB)</p>
                </div>
                <Input 
                  id="dropzone-file" 
                  type="file" 
                  accept=".csv,.txt"
                  className="hidden" 
                  onChange={handleFileChange}
                />
              </label>
            </div>
            {file && (
              <p className="text-sm text-zinc-300 flex items-center gap-2 mt-2">
                <span className="w-2 h-2 rounded-full bg-blue-500 inline-block"></span>
                Selected: <span className="font-semibold">{file.name}</span> ({(file.size / 1024).toFixed(2)} KB)
              </p>
            )}
          </div>

          {status === 'success' && (
            <div className="flex items-center gap-2 text-green-500 bg-green-500/10 p-3 rounded-md">
              <CheckCircle2 className="w-5 h-5" />
              <p className="text-sm">{message}</p>
            </div>
          )}

          {status === 'error' && (
            <div className="flex items-center gap-2 text-red-500 bg-red-500/10 p-3 rounded-md">
              <AlertCircle className="w-5 h-5" />
              <p className="text-sm">{message}</p>
            </div>
          )}

          <Button 
            onClick={handleUpload} 
            disabled={!file || status === 'uploading'}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white"
          >
            {status === 'uploading' ? (
              <>
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                Uploading...
              </>
            ) : (
              'Upload and Process'
            )}
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
