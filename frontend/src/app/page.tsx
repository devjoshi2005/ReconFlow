'use client'

import { useRouter } from 'next/navigation'
import { ArrowRight, ShieldCheck, Zap, Layers } from "lucide-react"

export default function LandingPage() {
  const router = useRouter()

  return (
    <div className="min-h-screen flex flex-col items-center justify-center relative overflow-hidden bg-[#09090b]">
      {/* Dynamic Background Elements */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-blue-600/20 rounded-full blur-[120px] pointer-events-none" />
      <div className="absolute bottom-0 left-0 w-[500px] h-[500px] bg-emerald-600/10 rounded-full blur-[100px] pointer-events-none" />
      <div className="absolute top-0 right-0 w-[600px] h-[600px] bg-purple-600/10 rounded-full blur-[100px] pointer-events-none" />

      <div className="relative z-10 max-w-5xl mx-auto px-4 text-center">
        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/5 border border-white/10 text-zinc-300 text-sm font-medium mb-8 animate-fade-in-up">
          <ShieldCheck className="w-4 h-4 text-emerald-400" />
          <span>Enterprise-Grade Financial Engine</span>
        </div>
        
        <h1 className="text-6xl md:text-8xl font-black tracking-tighter mb-6 bg-gradient-to-br from-white via-zinc-200 to-zinc-600 bg-clip-text text-transparent">
          ReconFlow
        </h1>
        
        <p className="text-xl md:text-2xl text-zinc-400 mb-12 max-w-2xl mx-auto leading-relaxed">
          The next generation reconciliation engine. Process millions of transactions across Payment Gateways and Banks with absolute precision.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-16 text-left max-w-4xl mx-auto">
          <div className="p-6 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md hover:bg-white/10 transition-colors">
            <Zap className="w-8 h-8 text-blue-400 mb-4" />
            <h3 className="text-lg font-bold text-white mb-2">Lightning Fast</h3>
            <p className="text-sm text-zinc-400">O(1) HashMap processing matching 50,000+ rows in milliseconds.</p>
          </div>
          <div className="p-6 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md hover:bg-white/10 transition-colors">
            <Layers className="w-8 h-8 text-purple-400 mb-4" />
            <h3 className="text-lg font-bold text-white mb-2">Two-Chain Matching</h3>
            <p className="text-sm text-zinc-400">Ledger to Gateway (Chain A) and Gateway to Bank (Chain B) full coverage.</p>
          </div>
          <div className="p-6 rounded-2xl bg-white/5 border border-white/10 backdrop-blur-md hover:bg-white/10 transition-colors">
            <ShieldCheck className="w-8 h-8 text-emerald-400 mb-4" />
            <h3 className="text-lg font-bold text-white mb-2">Idempotent Safe</h3>
            <p className="text-sm text-zinc-400">S3, SQS DLQ, and Redis guarantees no duplicate processing.</p>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <button 
            onClick={() => router.push('/login')}
            className="group px-8 py-4 rounded-xl bg-white text-black font-bold text-lg hover:scale-105 active:scale-95 transition-all flex items-center gap-2 shadow-[0_0_40px_-10px_rgba(255,255,255,0.5)]"
          >
            Access Dashboard
            <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
          </button>
        </div>
      </div>
    </div>
  )
}
