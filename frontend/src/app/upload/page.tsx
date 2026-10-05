'use client'

import { useState } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { UploadCloud, CheckCircle2, AlertCircle, Loader2, Calendar, ArrowRight } from 'lucide-react'
import { useRouter } from 'next/navigation'

export default function UploadPage() {
  const router = useRouter()
  const [gatewayFile, setGatewayFile] = useState<File | null>(null)
  const [bankFile, setBankFile] = useState<File | null>(null)
  const [ledgerFile, setLedgerFile] = useState<File | null>(null)
  const [gateway, setGateway] = useState<string>('RAZORPAY')
  const [bankName, setBankName] = useState<string>('HDFC')
  const [settlementDate, setSettlementDate] = useState<string>(new Date().toISOString().split('T')[0])
  
  const [status, setStatus] = useState<'idle' | 'uploading' | 'success' | 'error' | 'partial'>('idle')
  const [messages, setMessages] = useState<{type: 'success' | 'warning' | 'error', text: string}[]>([])

  const handleGatewayFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setGatewayFile(e.target.files[0])
      setStatus('idle')
      setMessages([])
    }
  }

  const handleBankFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setBankFile(e.target.files[0])
      setStatus('idle')
      setMessages([])
    }
  }

  const handleLedgerFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setLedgerFile(e.target.files[0])
      setStatus('idle')
      setMessages([])
    }
  }

  const uploadSingleFile = async (file: File, sourceType: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('sourceType', sourceType)
    formData.append('settlementDate', settlementDate)
    
    if (sourceType === 'GATEWAY') {
      formData.append('gateway', gateway)
    } else {
      formData.append('bankName', bankName)
    }

    const response = await fetch('http://localhost:8080/api/v1/upload', {
      method: 'POST',
      body: formData,
    })

    if (!response.ok) {
      const err = await response.text()
      try {
        const parsed = JSON.parse(err)
        throw new Error(parsed.error || parsed.message || 'Unknown error')
      } catch (e) {
        throw new Error(err)
      }
    }
    
    return await response.json()
  }

  const handleUpload = async () => {
    if (!gatewayFile && !bankFile && !ledgerFile) {
      setStatus('error')
      setMessages([{ type: 'error', text: 'Please select at least one file to upload.' }])
      return
    }

    if (!settlementDate) {
      setStatus('error')
      setMessages([{ type: 'error', text: 'Settlement date is required.' }])
      return
    }

    setStatus('uploading')
    setMessages([])
    const newMessages: {type: 'success' | 'warning' | 'error', text: string}[] = []
    let hasError = false
    let hasSuccess = false

    try {
      // 1. LEDGER FIRST
      if (ledgerFile) {
        try {
          const res = await uploadSingleFile(ledgerFile, 'LEDGER')
          hasSuccess = true
          newMessages.push({ type: 'success', text: res.message || `Ledger uploaded successfully.` })
          setLedgerFile(null)
          // Wait 2 seconds for DB persistence
          await new Promise(r => setTimeout(r, 2000))
        } catch (e: any) {
          hasError = true
          newMessages.push({ type: 'error', text: `Ledger upload failed: ${e.message}` })
        }
      }

      // 2. GATEWAY SECOND
      if (gatewayFile) {
        try {
          const res = await uploadSingleFile(gatewayFile, 'GATEWAY')
          hasSuccess = true
          if (res.chainBPending && !bankFile) {
            newMessages.push({ type: 'warning', text: res.message })
          } else {
            newMessages.push({ type: 'success', text: `Gateway file uploaded successfully.` })
          }
          setGatewayFile(null)
          // Wait 2 seconds for Chain A to complete
          await new Promise(r => setTimeout(r, 2000))
        } catch (e: any) {
          hasError = true
          newMessages.push({ type: 'error', text: `Gateway upload failed: ${e.message}` })
        }
      }

      // 3. BANK STATEMENT LAST
      if (bankFile) {
        try {
          const res = await uploadSingleFile(bankFile, 'BANK')
          hasSuccess = true
          if (res.chainBPending && !gatewayFile) {
            newMessages.push({ type: 'warning', text: res.message })
          } else {
            newMessages.push({ type: 'success', text: `Bank statement uploaded successfully.` })
          }
          setBankFile(null)
        } catch (e: any) {
          hasError = true
          newMessages.push({ type: 'error', text: `Bank statement upload failed: ${e.message}` })
        }
      }

      setMessages(newMessages)
      
      if (hasError && hasSuccess) {
        setStatus('partial')
      } else if (hasError) {
        setStatus('error')
      } else {
        setStatus('success')
      }

    } catch (error) {
      setStatus('error')
      setMessages([{ type: 'error', text: 'An unexpected network error occurred.' }])
    }
  }

  return (
    <div className="max-w-5xl mx-auto space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
      <div>
        <h2 className="text-3xl font-black tracking-tight bg-gradient-to-r from-white to-zinc-400 bg-clip-text text-transparent">Upload Settlement Files</h2>
        <p className="text-zinc-400">Upload your Payment Gateway and Bank Statement CSVs for the same settlement date to trigger End-to-End Reconciliation.</p>
      </div>

      <div className="grid lg:grid-cols-3 md:grid-cols-2 gap-6">
        {/* Gateway Upload */}
        <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50 shadow-2xl transition-all hover:border-zinc-700/50">
          <CardHeader>
            <CardTitle className="text-zinc-100">1. Payment Gateway Data</CardTitle>
            <CardDescription className="text-zinc-400">
              CSV export from your payment processor.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label className="text-zinc-300">Gateway Provider</Label>
              <Select value={gateway} onValueChange={(val: any) => setGateway(val)}>
                <SelectTrigger className="bg-zinc-950/50 border-zinc-800 text-zinc-100 focus:ring-blue-500 transition-colors">
                  <SelectValue placeholder="Select Gateway" />
                </SelectTrigger>
                <SelectContent className="bg-zinc-900 border-zinc-800 text-zinc-100">
                  <SelectItem value="RAZORPAY">Razorpay</SelectItem>
                  <SelectItem value="PHONEPE">PhonePe</SelectItem>
                  <SelectItem value="PAYU">PayU</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label className="text-zinc-300">Gateway CSV File</Label>
              <div className="flex items-center justify-center w-full">
                <label htmlFor="gateway-file" className="flex flex-col items-center justify-center w-full h-32 border-2 border-zinc-800 border-dashed rounded-xl cursor-pointer bg-zinc-950/30 hover:bg-zinc-800/50 hover:border-blue-500/50 transition-all group">
                  <div className="flex flex-col items-center justify-center pt-5 pb-6">
                    <UploadCloud className="w-8 h-8 mb-2 text-zinc-500 group-hover:text-blue-400 transition-colors" />
                    <p className="mb-2 text-sm text-zinc-400"><span className="font-semibold text-blue-400">Click to upload</span></p>
                  </div>
                  <Input id="gateway-file" type="file" accept=".csv,.txt" className="hidden" onChange={handleGatewayFileChange} />
                </label>
              </div>
              {gatewayFile && (
                <div className="text-sm text-zinc-300 flex items-center gap-2 mt-2 bg-blue-500/10 p-2 rounded-md border border-blue-500/20">
                  <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
                  <span className="truncate max-w-[200px] font-medium">{gatewayFile.name}</span> 
                  <span className="text-zinc-500 text-xs">({(gatewayFile.size / 1024).toFixed(2)} KB)</span>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Bank Upload */}
        <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50 shadow-2xl transition-all hover:border-zinc-700/50">
          <CardHeader>
            <CardTitle className="text-zinc-100">2. Bank Statement Data</CardTitle>
            <CardDescription className="text-zinc-400">
              CSV export from your corporate bank account.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label className="text-zinc-300">Bank Name</Label>
              <Select value={bankName} onValueChange={(val: any) => setBankName(val)}>
                <SelectTrigger className="bg-zinc-950/50 border-zinc-800 text-zinc-100 focus:ring-blue-500 transition-colors">
                  <SelectValue placeholder="Select Bank" />
                </SelectTrigger>
                <SelectContent className="bg-zinc-900 border-zinc-800 text-zinc-100">
                  <SelectItem value="HDFC">HDFC Bank</SelectItem>
                  <SelectItem value="ICICI">ICICI Bank</SelectItem>
                  <SelectItem value="SBI">State Bank of India</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label className="text-zinc-300">Bank CSV File</Label>
              <div className="flex items-center justify-center w-full">
                <label htmlFor="bank-file" className="flex flex-col items-center justify-center w-full h-32 border-2 border-zinc-800 border-dashed rounded-xl cursor-pointer bg-zinc-950/30 hover:bg-zinc-800/50 hover:border-blue-500/50 transition-all group">
                  <div className="flex flex-col items-center justify-center pt-5 pb-6">
                    <UploadCloud className="w-8 h-8 mb-2 text-zinc-500 group-hover:text-blue-400 transition-colors" />
                    <p className="mb-2 text-sm text-zinc-400"><span className="font-semibold text-blue-400">Click to upload</span></p>
                  </div>
                  <Input id="bank-file" type="file" accept=".csv,.txt" className="hidden" onChange={handleBankFileChange} />
                </label>
              </div>
              {bankFile && (
                <div className="text-sm text-zinc-300 flex items-center gap-2 mt-2 bg-blue-500/10 p-2 rounded-md border border-blue-500/20">
                  <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
                  <span className="truncate max-w-[200px] font-medium">{bankFile.name}</span> 
                  <span className="text-zinc-500 text-xs">({(bankFile.size / 1024).toFixed(2)} KB)</span>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {/* Ledger Upload */}
        <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50 shadow-2xl transition-all hover:border-zinc-700/50">
          <CardHeader>
            <CardTitle className="text-zinc-100">3. Ledger DB Data</CardTitle>
            <CardDescription className="text-zinc-400">
              CSV export from your internal ledger database.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label className="text-zinc-300">Gateway Type</Label>
              <Select value={gateway} onValueChange={(val: any) => setGateway(val)}>
                <SelectTrigger className="bg-zinc-950/50 border-zinc-800 text-zinc-100 focus:ring-blue-500 transition-colors">
                  <SelectValue placeholder="Select Gateway" />
                </SelectTrigger>
                <SelectContent className="bg-zinc-900 border-zinc-800 text-zinc-100">
                  <SelectItem value="RAZORPAY">Razorpay</SelectItem>
                  <SelectItem value="PHONEPE">PhonePe</SelectItem>
                  <SelectItem value="PAYU">PayU</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label className="text-zinc-300">Ledger CSV File</Label>
              <div className="flex items-center justify-center w-full">
                <label htmlFor="ledger-file" className="flex flex-col items-center justify-center w-full h-32 border-2 border-zinc-800 border-dashed rounded-xl cursor-pointer bg-zinc-950/30 hover:bg-zinc-800/50 hover:border-blue-500/50 transition-all group">
                  <div className="flex flex-col items-center justify-center pt-5 pb-6">
                    <UploadCloud className="w-8 h-8 mb-2 text-zinc-500 group-hover:text-blue-400 transition-colors" />
                    <p className="mb-2 text-sm text-zinc-400"><span className="font-semibold text-blue-400">Click to upload</span></p>
                  </div>
                  <Input id="ledger-file" type="file" accept=".csv,.txt" className="hidden" onChange={handleLedgerFileChange} />
                </label>
              </div>
              {ledgerFile && (
                <div className="text-sm text-zinc-300 flex items-center gap-2 mt-2 bg-blue-500/10 p-2 rounded-md border border-blue-500/20">
                  <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
                  <span className="truncate max-w-[200px] font-medium">{ledgerFile.name}</span> 
                  <span className="text-zinc-500 text-xs">({(ledgerFile.size / 1024).toFixed(2)} KB)</span>
                </div>
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      <Card className="bg-zinc-900/60 backdrop-blur-md border-zinc-800/50 shadow-2xl">
        <CardContent className="p-6">
          <div className="flex flex-col md:flex-row items-center justify-between gap-6">
            <div className="w-full md:w-1/2 space-y-2">
              <Label className="text-zinc-300 text-lg flex items-center gap-2">
                <Calendar className="w-5 h-5 text-blue-400" />
                Settlement Date
              </Label>
              <p className="text-sm text-zinc-500 mb-2">This links the Gateway and Bank data together.</p>
              <Input 
                type="date" 
                value={settlementDate}
                onChange={(e) => setSettlementDate(e.target.value)}
                className="bg-zinc-950/50 border-zinc-800 text-zinc-100 py-6 text-lg rounded-xl focus:ring-blue-500 transition-all"
              />
            </div>
            
            <div className="w-full md:w-auto flex flex-col items-end gap-4">
              <Button 
                onClick={handleUpload} 
                disabled={status === 'uploading' || (!gatewayFile && !bankFile && !ledgerFile)}
                className="w-full md:w-64 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white shadow-[0_0_20px_rgba(37,99,235,0.3)] py-6 text-lg rounded-xl transition-all hover:scale-[1.02]"
              >
                {status === 'uploading' ? (
                  <><Loader2 className="mr-2 h-5 w-5 animate-spin" /> Uploading...</>
                ) : (
                  <><UploadCloud className="mr-2 h-5 w-5" /> Upload Transactions</>
                )}
              </Button>
              {status === 'success' && (
                <Button 
                  onClick={() => router.push('/dashboard')}
                  variant="outline"
                  className="w-full md:w-64 border-emerald-500/50 text-emerald-400 hover:bg-emerald-500/10 py-6 text-lg rounded-xl transition-all hover:scale-[1.02]"
                >
                  View Reconciliation Results <ArrowRight className="ml-2 w-5 h-5" />
                </Button>
              )}
            </div>
          </div>

          {messages.length > 0 && (
            <div className="mt-6 space-y-3">
              {messages.map((msg, idx) => (
                <div key={idx} className={`flex items-center gap-3 p-4 rounded-xl border backdrop-blur-sm transition-all animate-in fade-in slide-in-from-bottom-2 ${
                  msg.type === 'success' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' : 
                  msg.type === 'warning' ? 'bg-yellow-500/10 text-yellow-400 border-yellow-500/20' : 
                  'bg-red-500/10 text-red-400 border-red-500/20'
                }`}>
                  {msg.type === 'success' && <CheckCircle2 className="w-5 h-5 shrink-0" />}
                  {msg.type === 'warning' && <AlertCircle className="w-5 h-5 shrink-0" />}
                  {msg.type === 'error' && <AlertCircle className="w-5 h-5 shrink-0" />}
                  <span className="font-medium text-sm">{msg.text}</span>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
