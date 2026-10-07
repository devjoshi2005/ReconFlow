import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Sidebar } from "@/components/layout/Sidebar";
import { cookies } from "next/headers";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "ReconFlow",
  description: "Next Generation Reconciliation Engine",
};

import { AuthProvider } from "@/components/AuthProvider";
import { RouteGuard } from "@/lib/RouteGuard";
import { AppLayoutWrapper } from "@/components/AppLayoutWrapper";

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const cookieStore = await cookies();
  const token = cookieStore.get("accessToken")?.value;
  const isAuth = !!token;

  return (
    <html lang="en" className="dark">
      <body className={`${inter.className} bg-[#09090b] text-zinc-50 min-h-screen flex flex-col md:flex-row selection:bg-blue-500/30 selection:text-blue-200`}>
        {/* Abstract Background Elements for Premium Look */}
        <div className="fixed inset-0 overflow-hidden pointer-events-none z-0">
          <div className="absolute -top-[20%] -left-[10%] w-[50%] h-[50%] rounded-full bg-blue-900/10 blur-[120px]" />
          <div className="absolute top-[60%] -right-[10%] w-[40%] h-[40%] rounded-full bg-indigo-900/10 blur-[100px]" />
        </div>

        <AuthProvider>
          <RouteGuard>
            <AppLayoutWrapper>
              {children}
            </AppLayoutWrapper>
          </RouteGuard>
        </AuthProvider>
      </body>
    </html>
  );
}
