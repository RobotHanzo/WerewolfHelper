
import React, { useState } from 'react';
import { Code, Settings, Copy, Check } from 'lucide-react';

interface IntegrationGuideProps {
  onClose: () => void;
}

export const IntegrationGuide: React.FC<IntegrationGuideProps> = ({ onClose }) => {
  const [copied, setCopied] = useState(false);

  const javaCode = `
// =================================================================
// JAVA DISCORD BOT INTEGRATION GUIDE (Using Javalin + JDA)
// =================================================================

// 1. Add dependencies to pom.xml / build.gradle:
//    - io.javalin:javalin:5.x
//    - com.fasterxml.jackson.core:jackson-databind

public class WerewolfDashboardServer {
    private static final int PORT = 8080;
    private final WerewolfGameManager gameManager; // Your existing game logic class

    public WerewolfDashboardServer(WerewolfGameManager gameManager) {
        this.gameManager = gameManager;
    }

    public void start() {
        Javalin app = Javalin.create(config -> {
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost())); 
        }).start(PORT);

        // API: Get Game State
        app.get("/api/state", ctx -> {
            // Verify 'Authorization' header contains valid JWT from Discord OAuth
            String token = ctx.header("Authorization");
            if (!isValidAdminToken(token)) {
                throw new ForbiddenResponse(); 
            }
            ctx.json(gameManager.getCurrentGameState());
        });

        // API: Admin Actions
        app.post("/api/action", ctx -> {
             if (!isValidAdminToken(ctx.header("Authorization"))) {
                throw new ForbiddenResponse();
            }
            // Parse action: { "playerId": "...", "action": "kill" }
            GameAction action = ctx.bodyAsClass(GameAction.class);
            gameManager.handleAdminAction(action);
            ctx.json(Map.of("status", "success"));
        });
    }
}
  `.trim();

  const handleCopy = () => {
    navigator.clipboard.writeText(javaCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const btnStyle = "px-4 py-2 rounded-lg font-medium transition-all active:scale-95 flex items-center justify-center gap-2";
  const btnPrimary = "bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-900/20";

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-slate-900 w-full max-w-4xl max-h-[90vh] rounded-2xl border border-slate-700 shadow-2xl overflow-hidden flex flex-col">
        <div className="p-6 border-b border-slate-800 flex justify-between items-center">
          <div className="flex items-center gap-3">
            <Code className="w-6 h-6 text-indigo-400" />
            <h2 className="text-xl font-bold text-white">Backend Integration Instructions</h2>
          </div>
          <button onClick={onClose} className="p-2 hover:bg-slate-800 rounded-full">
            <Settings className="w-5 h-5" />
          </button>
        </div>
        
        <div className="p-6 overflow-y-auto space-y-6">
          <div className="bg-indigo-900/20 p-4 rounded-lg border border-indigo-500/30">
            <h3 className="font-semibold text-indigo-200 mb-2">Architecture Overview</h3>
            <p className="text-slate-300 text-sm leading-relaxed">
              This dashboard acts as a frontend client. To make it functional, you need to expose a REST API from your Java Discord Bot. 
              The dashboard will authenticate users via Discord OAuth2, then send their access token to your bot to verify Admin permissions.
            </p>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <span className="text-slate-400 text-sm uppercase tracking-wider font-bold">Java Server Implementation</span>
              <button 
                onClick={handleCopy}
                className="flex items-center gap-2 text-xs bg-slate-800 hover:bg-slate-700 px-3 py-1.5 rounded-md border border-slate-700 transition-colors"
              >
                {copied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                {copied ? 'Copied' : 'Copy Code'}
              </button>
            </div>
            <div className="relative">
              <pre className="bg-slate-950 p-4 rounded-lg border border-slate-800 overflow-x-auto text-sm font-mono text-slate-300 leading-relaxed">
                {javaCode}
              </pre>
            </div>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
             <div className="p-4 rounded-lg border border-slate-800 bg-slate-900/50">
                <h4 className="font-medium text-slate-200 mb-2">API Specification</h4>
                <ul className="text-sm text-slate-400 space-y-1 list-disc list-inside">
                  <li><code className="text-indigo-400">GET /api/state</code> - JSON object matching GameState interface</li>
                  <li><code className="text-indigo-400">POST /api/action</code> - Command execution</li>
                  <li><code className="text-indigo-400">POST /api/auth</code> - OAuth Code Exchange</li>
                </ul>
             </div>
             <div className="p-4 rounded-lg border border-slate-800 bg-slate-900/50">
                <h4 className="font-medium text-slate-200 mb-2">OAuth Config</h4>
                <p className="text-sm text-slate-400">
                  Register an application in the Discord Developer Portal. Set the Redirect URI to your dashboard domain.
                  Use <code className="text-orange-400">guilds</code> and <code className="text-orange-400">identify</code> scopes to verify server membership and roles.
                </p>
             </div>
          </div>
        </div>

        <div className="p-6 border-t border-slate-800 bg-slate-900 flex justify-end">
          <button onClick={onClose} className={`${btnStyle} ${btnPrimary}`}>
            Close Guide
          </button>
        </div>
      </div>
    </div>
  );
};
