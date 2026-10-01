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

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const cookieStore = await cookies();
  const role = cookieStore.get("recon_role")?.value;
  const isAuth = !!role;

  return (
    <html lang="en" className="dark">
      <body className={`${inter.className} bg-[#09090b] text-zinc-50 min-h-screen flex flex-col md:flex-row selection:bg-blue-500/30 selection:text-blue-200`}>
        {/* Abstract Background Elements for Premium Look */}
        <div className="fixed inset-0 overflow-hidden pointer-events-none z-0">
          <div className="absolute -top-[20%] -left-[10%] w-[50%] h-[50%] rounded-full bg-blue-900/10 blur-[120px]" />
          <div className="absolute top-[60%] -right-[10%] w-[40%] h-[40%] rounded-full bg-indigo-900/10 blur-[100px]" />
        </div>

        {isAuth ? (
          <>
            <Sidebar role={role} />
            <div className="flex-1 flex flex-col md:ml-64 min-h-screen relative z-10">
              {/* Mobile Header Spacer to prevent content overlap since Sidebar has fixed hamburger */}
              <div className="md:hidden h-16 w-full flex items-center px-4 bg-zinc-950/80 backdrop-blur-md border-b border-zinc-800/50 sticky top-0 z-30">
                <h1 className="text-xl font-black tracking-tighter bg-gradient-to-br from-blue-400 to-indigo-600 bg-clip-text text-transparent">ReconFlow</h1>
              </div>
              <main className="flex-1 p-4 md:p-8 overflow-y-auto">
                {children}
              </main>
            </div>
          </>
        ) : (
          <main className="flex-1 w-full z-10 relative">{children}</main>
        )}
      </body>
    </html>
  );
}
