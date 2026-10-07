"use client";

import React, { useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import { useRouter } from "next/navigation";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();
  const router = useRouter();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError("");

    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';
      const res = await fetch(`${baseUrl}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email, password }),
      });

      const data = await res.json();

      if (res.ok) {
        login({ userId: data.userId, role: data.role });
      } else {
        setError(data.error || "Login failed");
      }
    } catch (err) {
      setError("An unexpected error occurred. Is the server running?");
    } finally {
      setIsLoading(false);
    }
  };

  const setCredentials = (role: string) => {
    setEmail(role);
    setPassword(role);
  };

  return (
    <div className="flex min-h-screen w-full items-center justify-center bg-[#09090b] relative overflow-hidden">
      {/* Background Orbs */}
      <div className="absolute top-[20%] left-[20%] w-[400px] h-[400px] rounded-full bg-blue-900/20 blur-[120px] mix-blend-screen pointer-events-none" />
      <div className="absolute bottom-[20%] right-[20%] w-[300px] h-[300px] rounded-full bg-indigo-900/20 blur-[100px] mix-blend-screen pointer-events-none" />

      <div className="z-10 w-full max-w-md p-8 rounded-3xl bg-zinc-900/50 border border-zinc-800/50 backdrop-blur-xl shadow-2xl relative">
        <div className="mb-8 text-center">
          <h1 className="text-3xl font-black tracking-tighter bg-gradient-to-br from-blue-400 to-indigo-600 bg-clip-text text-transparent mb-2">
            ReconFlow
          </h1>
          <p className="text-zinc-400 text-sm">Sign in to your account</p>
        </div>

        <form onSubmit={handleLogin} className="space-y-4">
          {error && (
            <div className="p-3 rounded-xl bg-red-900/30 border border-red-800/50 text-red-200 text-sm text-center">
              {error}
            </div>
          )}

          <div className="space-y-1">
            <label className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Email/Username</label>
            <input
              type="text"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-4 py-3 rounded-xl bg-zinc-950/50 border border-zinc-800 text-zinc-100 focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all placeholder-zinc-600"
              placeholder="admin"
              required
            />
          </div>

          <div className="space-y-1">
            <label className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-3 rounded-xl bg-zinc-950/50 border border-zinc-800 text-zinc-100 focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition-all placeholder-zinc-600"
              placeholder="••••••••"
              required
            />
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-3 px-4 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-medium shadow-lg shadow-blue-900/20 transition-all disabled:opacity-50 disabled:cursor-not-allowed mt-4 relative overflow-hidden group"
          >
            <div className="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300 ease-in-out" />
            <span className="relative">
              {isLoading ? "Authenticating..." : "Sign In"}
            </span>
          </button>
        </form>

        <div className="mt-8 pt-6 border-t border-zinc-800/50">
          <p className="text-xs text-center text-zinc-500 mb-4 uppercase tracking-widest font-semibold">Demo Accounts</p>
          <div className="grid grid-cols-3 gap-2">
            <button
              type="button"
              onClick={() => setCredentials("admin")}
              className="py-2 text-xs font-medium text-zinc-300 bg-zinc-800/50 hover:bg-blue-900/30 hover:text-blue-300 rounded-lg border border-zinc-700/50 hover:border-blue-800/50 transition-all"
            >
              Admin
            </button>
            <button
              type="button"
              onClick={() => setCredentials("manager")}
              className="py-2 text-xs font-medium text-zinc-300 bg-zinc-800/50 hover:bg-indigo-900/30 hover:text-indigo-300 rounded-lg border border-zinc-700/50 hover:border-indigo-800/50 transition-all"
            >
              Manager
            </button>
            <button
              type="button"
              onClick={() => setCredentials("analyst")}
              className="py-2 text-xs font-medium text-zinc-300 bg-zinc-800/50 hover:bg-purple-900/30 hover:text-purple-300 rounded-lg border border-zinc-700/50 hover:border-purple-800/50 transition-all"
            >
              Analyst
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
