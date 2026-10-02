import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { Settings as SettingsIcon, Bell, Shield, Database, Key } from 'lucide-react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

export default function SettingsPage() {
  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div>
        <h2 className="text-3xl font-bold tracking-tight">System Settings</h2>
        <p className="text-zinc-400">Manage application preferences, API keys, and notification rules.</p>
      </div>

      <Tabs defaultValue="general" className="w-full">
        <TabsList className="bg-zinc-900 border-zinc-800">
          <TabsTrigger value="general" className="data-[state=active]:bg-zinc-800">General</TabsTrigger>
          <TabsTrigger value="notifications" className="data-[state=active]:bg-zinc-800">Notifications</TabsTrigger>
          <TabsTrigger value="integrations" className="data-[state=active]:bg-zinc-800">Integrations</TabsTrigger>
          <TabsTrigger value="security" className="data-[state=active]:bg-zinc-800">Security</TabsTrigger>
        </TabsList>
        
        <TabsContent value="general" className="mt-6 space-y-4">
          <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <SettingsIcon className="w-5 h-5 text-blue-400" />
                General Preferences
              </CardTitle>
              <CardDescription className="text-zinc-400">Configure global reconciliation settings.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-1">
                <Label>Default Currency</Label>
                <div className="p-3 bg-zinc-950 border border-zinc-800 rounded-md text-sm text-zinc-300">
                  INR (Indian Rupee)
                </div>
              </div>
              <div className="space-y-1">
                <Label>Timezone</Label>
                <div className="p-3 bg-zinc-950 border border-zinc-800 rounded-md text-sm text-zinc-300">
                  Asia/Kolkata (IST)
                </div>
              </div>
              <Button className="mt-4 bg-blue-600 hover:bg-blue-700 text-white">Save Changes</Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="notifications" className="mt-6 space-y-4">
          <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Bell className="w-5 h-5 text-indigo-400" />
                Notification Rules
              </CardTitle>
              <CardDescription className="text-zinc-400">Manage alerts for threshold breaches and failed jobs.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center justify-between p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
                <div>
                  <h4 className="font-medium text-sm">High Value Exception Alerts</h4>
                  <p className="text-xs text-zinc-400">Email admins when discrepancy exceeds ₹50,000</p>
                </div>
                <div className="w-10 h-5 bg-blue-600 rounded-full flex items-center p-1 cursor-pointer">
                  <div className="w-4 h-4 bg-white rounded-full translate-x-4 shadow-sm" />
                </div>
              </div>
              <div className="flex items-center justify-between p-4 bg-zinc-950 border border-zinc-800 rounded-xl">
                <div>
                  <h4 className="font-medium text-sm">Batch Job Failures</h4>
                  <p className="text-xs text-zinc-400">Send Slack notification if recon pipeline fails</p>
                </div>
                <div className="w-10 h-5 bg-blue-600 rounded-full flex items-center p-1 cursor-pointer">
                  <div className="w-4 h-4 bg-white rounded-full translate-x-4 shadow-sm" />
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="integrations" className="mt-6 space-y-4">
          <Card className="bg-zinc-900 border-zinc-800 text-zinc-50">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Database className="w-5 h-5 text-emerald-400" />
                API & Integrations
              </CardTitle>
              <CardDescription className="text-zinc-400">Configure connection settings to external gateways.</CardDescription>
            </CardHeader>
            <CardContent>
               <div className="space-y-4">
                 <div className="border border-zinc-800 rounded-xl p-4">
                   <div className="flex justify-between items-center mb-2">
                     <h4 className="font-medium">Razorpay API</h4>
                     <Badge variant="outline" className="bg-emerald-500/10 text-emerald-500 border-emerald-500/20">Connected</Badge>
                   </div>
                   <p className="text-xs text-zinc-500 font-mono">Last synced: 2 minutes ago</p>
                 </div>
                 <div className="border border-zinc-800 rounded-xl p-4">
                   <div className="flex justify-between items-center mb-2">
                     <h4 className="font-medium">PhonePe PG</h4>
                     <Badge variant="outline" className="bg-zinc-500/10 text-zinc-400 border-zinc-500/20">Not Configured</Badge>
                   </div>
                   <Button variant="outline" size="sm" className="mt-2 text-xs border-zinc-700 bg-zinc-800 hover:bg-zinc-700">Configure Credentials</Button>
                 </div>
               </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="security" className="mt-6 space-y-4">
          <Card className="bg-zinc-900 border-zinc-800 text-zinc-50 flex flex-col items-center justify-center min-h-[300px]">
            <CardHeader className="text-center">
              <div className="mx-auto bg-red-500/10 p-4 rounded-full border border-red-500/20 mb-4 inline-flex">
                <Shield className="w-8 h-8 text-red-400" />
              </div>
              <CardTitle className="text-xl">Security & Compliance</CardTitle>
              <CardDescription className="text-zinc-400 max-w-sm mx-auto mt-2">
                Advanced security configurations like IP Whitelisting and 2FA enforcement are managed by Super Admins only.
              </CardDescription>
            </CardHeader>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}

function Badge({ children, className, variant }: { children: React.ReactNode, className?: string, variant?: string }) {
  return <span className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${className}`}>{children}</span>
}
