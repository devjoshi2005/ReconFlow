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
  const [minAmount, setMinAmount] = useState<string>('0.01')
  const [maxAmount, setMaxAmount] = useState<string>('10000000')
  
  const [isLedgerUploading, setIsLedgerUploading] = useState(false)
  const [isGatewayUploading, setIsGatewayUploading] = useState(false)
  const [isBankUploading, setIsBankUploading] = useState(false)
  
  const [messages, setMessages] = useState<{type: 'success' | 'warning' | 'error', text: string}[]>([])

  const handleGatewayFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setGatewayFile(e.target.files[0])
    }
  }

  const handleBankFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setBankFile(e.target.files[0])
    }
  }

  const handleLedgerFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setLedgerFile(e.target.files[0])
    }
  }

  const uploadSingleFile = async (file: File, sourceType: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('sourceType', sourceType)
    formData.append('settlementDate', settlementDate)
    formData.append('minAmount', minAmount)
    formData.append('maxAmount', maxAmount)
    
    if (sourceType === 'GATEWAY') {
      formData.append('gateway', gateway)
    } else {
      formData.append('bankName', bankName)
    }

    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';
    const response = await fetch(`${baseUrl}/upload`, {
      method: 'POST',
      body: formData,
      credentials: 'include'
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

  const handleUploadLedger = async () => {
    if (!ledgerFile) return
    setIsLedgerUploading(true)
    try {
      const res = await uploadSingleFile(ledgerFile, 'LEDGER')
      setMessages(prev => [{ type: 'success', text: res.message || `Ledger uploaded successfully.` }, ...prev])
      setLedgerFile(null)
    } catch (e: any) {
      setMessages(prev => [{ type: 'error', text: `Ledger upload failed: ${e.message}` }, ...prev])
    } finally {
      setIsLedgerUploading(false)
    }
  }

  const handleUploadGateway = async () => {
    if (!gatewayFile) return
    setIsGatewayUploading(true)
    try {
      const res = await uploadSingleFile(gatewayFile, 'GATEWAY')
      setMessages(prev => [{ type: 'success', text: res.message || `Gateway file uploaded successfully.` }, ...prev])
      setGatewayFile(null)
    } catch (e: any) {
      setMessages(prev => [{ type: 'error', text: `Gateway upload failed: ${e.message}` }, ...prev])
    } finally {
      setIsGatewayUploading(false)
    }
  }

  const handleUploadBank = async () => {
    if (!bankFile) return
    setIsBankUploading(true)
    try {
      const res = await uploadSingleFile(bankFile, 'BANK')
      setMessages(prev => [{ type: 'success', text: res.message || `Bank statement uploaded successfully.` }, ...prev])
      setBankFile(null)
    } catch (e: any) {
      setMessages(prev => [{ type: 'error', text: `Bank statement upload failed: ${e.message}` }, ...prev])
    } finally {
      setIsBankUploading(false)
    }
  }

  return (
    <div className="max-w-6xl mx-auto space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-12">
      <div>
        <h2 className="text-3xl font-black tracking-tight bg-gradient-to-r from-white to-zinc-400 bg-clip-text text-transparent">Upload Settlement Files</h2>
        <p className="text-zinc-400">Upload your Ledger, Payment Gateway, and Bank Statement CSVs in sequence for accurate reconciliation.</p>
      </div>

      <Card className="bg-zinc-900/60 backdrop-blur-md border-zinc-800/50 shadow-2xl">
        <CardContent className="p-6">
          <div className="flex flex-col md:flex-row items-center justify-between gap-6">
            <div className="w-full md:w-2/3 space-y-2">
              <Label className="text-zinc-300 text-lg flex items-center gap-2">
                <Calendar className="w-5 h-5 text-blue-400" />
                Global Settlement Date
              </Label>
              <p className="text-sm text-zinc-500 mb-2">This date links your Gateway and Bank files together. Ensure it matches the settlement date of the uploads below.</p>
              <Input 
                type="date" 
                value={settlementDate}
                onChange={(e) => setSettlementDate(e.target.value)}
                className="bg-zinc-950/50 border-zinc-800 text-zinc-100 py-6 text-lg rounded-xl focus:ring-blue-500 transition-all w-full max-w-sm"
              />
            </div>
            <div className="w-full md:w-1/3 flex justify-end">
                <Button 
                  onClick={() => router.push('/dashboard')}
                  variant="outline"
                  className="w-full border-emerald-500/50 text-emerald-400 hover:bg-emerald-500/10 py-6 text-lg rounded-xl transition-all hover:scale-[1.02]"
                >
                  View Dashboard <ArrowRight className="ml-2 w-5 h-5" />
                </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="grid lg:grid-cols-3 gap-6">
        {/* Step 1: Ledger Upload */}
        <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50 shadow-2xl transition-all hover:border-zinc-700/50 flex flex-col">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="text-zinc-100">Step 1: Ledger Data</CardTitle>
            </div>
            <CardDescription className="text-zinc-400 h-10">
              CSV export from your internal ledger database.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 flex-grow flex flex-col justify-between">
            <div className="space-y-2">
              <Label className="text-zinc-300">Ledger CSV File</Label>
              <div className="flex items-center justify-center w-full">
                <label htmlFor="ledger-file" className="flex flex-col items-center justify-center w-full h-32 border-2 border-zinc-800 border-dashed rounded-xl cursor-pointer bg-zinc-950/30 hover:bg-zinc-800/50 hover:border-blue-500/50 transition-all group">
                  <div className="flex flex-col items-center justify-center pt-5 pb-6">
                    <UploadCloud className="w-8 h-8 mb-2 text-zinc-500 group-hover:text-blue-400 transition-colors" />
                    <p className="mb-2 text-sm text-zinc-400"><span className="font-semibold text-blue-400">Click to select</span></p>
                  </div>
                  <Input id="ledger-file" type="file" accept=".csv,.txt" className="hidden" onChange={handleLedgerFileChange} />
                </label>
              </div>
              {ledgerFile && (
                <div className="text-sm text-zinc-300 flex items-center gap-2 mt-2 bg-blue-500/10 p-2 rounded-md border border-blue-500/20">
                  <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
                  <span className="truncate max-w-[150px] font-medium">{ledgerFile.name}</span> 
                  <span className="text-zinc-500 text-xs">({(ledgerFile.size / 1024).toFixed(2)} KB)</span>
                </div>
              )}
            </div>
            <Button 
                onClick={handleUploadLedger} 
                disabled={!ledgerFile || isLedgerUploading}
                className="w-full mt-4 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white shadow-[0_0_15px_rgba(37,99,235,0.2)]"
            >
                {isLedgerUploading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Uploading...</> : <><UploadCloud className="mr-2 h-4 w-4" /> Upload Ledger</>}
            </Button>
          </CardContent>
        </Card>

        {/* Step 2: Gateway Upload */}
        <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50 shadow-2xl transition-all hover:border-zinc-700/50 flex flex-col">
          <CardHeader>
            <CardTitle className="text-zinc-100">Step 2: Payment Gateway</CardTitle>
            <CardDescription className="text-zinc-400 h-10">
              CSV export from your payment processor.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 flex-grow flex flex-col justify-between">
            <div className="space-y-4">
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
                      <p className="mb-2 text-sm text-zinc-400"><span className="font-semibold text-blue-400">Click to select</span></p>
                    </div>
                    <Input id="gateway-file" type="file" accept=".csv,.txt" className="hidden" onChange={handleGatewayFileChange} />
                  </label>
                </div>
                {gatewayFile && (
                  <div className="text-sm text-zinc-300 flex items-center gap-2 mt-2 bg-blue-500/10 p-2 rounded-md border border-blue-500/20">
                    <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
                    <span className="truncate max-w-[150px] font-medium">{gatewayFile.name}</span> 
                    <span className="text-zinc-500 text-xs">({(gatewayFile.size / 1024).toFixed(2)} KB)</span>
                  </div>
                )}
              </div>
              
              <div className="grid grid-cols-2 gap-4 pt-2">
                <div className="space-y-2">
                  <Label className="text-zinc-300 text-xs text-zinc-400">Min Amount (₹)</Label>
                  <Input 
                    type="number" 
                    value={minAmount}
                    onChange={(e) => setMinAmount(e.target.value)}
                    className="bg-zinc-950/50 border-zinc-800 text-zinc-100 text-sm rounded-lg focus:ring-blue-500"
                  />
                </div>
                <div className="space-y-2">
                  <Label className="text-zinc-300 text-xs text-zinc-400">Max Amount (₹)</Label>
                  <Input 
                    type="number" 
                    value={maxAmount}
                    onChange={(e) => setMaxAmount(e.target.value)}
                    className="bg-zinc-950/50 border-zinc-800 text-zinc-100 text-sm rounded-lg focus:ring-blue-500"
                  />
                </div>
              </div>
            </div>
            <Button 
                onClick={handleUploadGateway} 
                disabled={!gatewayFile || !settlementDate || isGatewayUploading}
                className="w-full mt-4 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white shadow-[0_0_15px_rgba(37,99,235,0.2)]"
            >
                {isGatewayUploading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Uploading...</> : <><UploadCloud className="mr-2 h-4 w-4" /> Upload Gateway</>}
            </Button>
          </CardContent>
        </Card>

        {/* Step 3: Bank Upload */}
        <Card className="bg-zinc-900/40 backdrop-blur-sm border-zinc-800/50 shadow-2xl transition-all hover:border-zinc-700/50 flex flex-col">
          <CardHeader>
            <CardTitle className="text-zinc-100">Step 3: Bank Statement</CardTitle>
            <CardDescription className="text-zinc-400 h-10">
              CSV export from your corporate bank account.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 flex-grow flex flex-col justify-between">
            <div className="space-y-4">
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
                      <p className="mb-2 text-sm text-zinc-400"><span className="font-semibold text-blue-400">Click to select</span></p>
                    </div>
                    <Input id="bank-file" type="file" accept=".csv,.txt" className="hidden" onChange={handleBankFileChange} />
                  </label>
                </div>
                {bankFile && (
                  <div className="text-sm text-zinc-300 flex items-center gap-2 mt-2 bg-blue-500/10 p-2 rounded-md border border-blue-500/20">
                    <div className="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></div>
                    <span className="truncate max-w-[150px] font-medium">{bankFile.name}</span> 
                    <span className="text-zinc-500 text-xs">({(bankFile.size / 1024).toFixed(2)} KB)</span>
                  </div>
                )}
              </div>
            </div>
            <Button 
                onClick={handleUploadBank} 
                disabled={!bankFile || !settlementDate || isBankUploading}
                className="w-full mt-4 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white shadow-[0_0_15px_rgba(37,99,235,0.2)]"
            >
                {isBankUploading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Uploading...</> : <><UploadCloud className="mr-2 h-4 w-4" /> Upload Bank</>}
            </Button>
          </CardContent>
        </Card>
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
    </div>
  )
}
