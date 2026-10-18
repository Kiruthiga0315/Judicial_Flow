import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { User, Eye, EyeOff, ArrowRight, AlertCircle, Sparkles, Scale, Clock, Building2, ShieldCheck } from 'lucide-react';
import { JudicialLogomark } from '../components/JudicialLogomark';
import { PrinciplesCarousel } from '../components/PrinciplesCarousel';
import bgBrush from '../assets/bg-brush.jpeg';

export const LoginPage: React.FC = () => {
  const { login, isLoading } = useAuth();
  const [username, setUsername] = useState<string>('');
  const [password, setPassword] = useState<string>('');
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await login(username.trim(), password);
    } catch (err: any) {
      setError(err.message || 'Authentication failed. Please verify credentials.');
    }
  };

  const handleQuickFill = (u: string, p: string) => {
    setUsername(u);
    setPassword(p);
    setError(null);
  };

  return (
    <div className="min-h-screen bg-jf-lavender relative flex items-center justify-center p-3 sm:p-6 lg:p-8 font-sans selection:bg-jf-apricot selection:text-jf-ink overflow-hidden">
      {/* Background Layer 1: Sweeping tactile brush texture fitting JudicialFlow */}
      <div
        className="absolute inset-0 bg-cover bg-center pointer-events-none opacity-25 mix-blend-multiply transition-opacity duration-700"
        style={{ backgroundImage: `url(${bgBrush})` }}
        aria-hidden="true"
      />

      {/* Background Layer 2: Atmospheric radial gradient scrim */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: `
            radial-gradient(circle at 50% 50%, rgba(253, 248, 226, 0.45) 0%, rgba(196, 195, 227, 0.3) 60%, rgba(80, 78, 118, 0.35) 100%)
          `,
        }}
        aria-hidden="true"
      />

      {/* Background Layer 3: Ambient focal glows behind card frame */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 rounded-full bg-jf-apricot/25 blur-3xl pointer-events-none" aria-hidden="true" />
      <div className="absolute bottom-1/4 right-1/4 w-96 h-96 rounded-full bg-jf-violet/15 blur-3xl pointer-events-none" aria-hidden="true" />

      {/* Centered rounded frame with 28px radius, multi-layer ambient glow & soft elevation */}
      <main className="w-full max-w-[1240px] bg-jf-cream/95 backdrop-blur-md rounded-card shadow-[0_25px_70px_-15px_rgba(80,78,118,0.25),0_0_80px_-20px_rgba(241,100,46,0.14)] overflow-hidden border border-white/60 dark:border-jf-border-subtle flex flex-col lg:flex-row relative z-10 animate-jf-frame">

        {/* =========================================================================
            LEFT PANEL: Registry Sign In Form (~46% width on desktop)
            ========================================================================= */}
        <section className="w-full lg:w-[46%] p-6 sm:p-10 lg:p-12 flex flex-col justify-between bg-jf-cream z-10">
          <div>
            {/* Logomark & Wordmark */}
            <div className="mb-8 animate-jf-fade-up">
              <JudicialLogomark size="md" />
            </div>

            {/* Heading & Subcopy */}
            <div className="mb-8 animate-jf-fade-up" style={{ animationDelay: '60ms' }}>
              <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-jf-ink">
                Sign in to <span className="text-jf-text-orange">JudicialFlow</span>
              </h1>
              <p className="mt-2 text-sm text-jf-ink-muted leading-relaxed">
                Fair, explainable hearing schedules for trial courts.
              </p>
            </div>

            {/* Error Announcement */}
            {error && (
              <div
                role="alert"
                aria-live="polite"
                className="mb-6 p-3.5 bg-jf-cream-darker border border-jf-text-orange/25 rounded-xl flex items-start gap-2.5 text-jf-text-orange text-xs animate-jf-slide-down"
              >
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5 text-jf-orange" />
                <span className="font-medium leading-normal">{error}</span>
              </div>
            )}

            {/* Login Form */}
            <form onSubmit={handleSubmit} className="space-y-5 animate-jf-fade-up" style={{ animationDelay: '120ms' }}>
              {/* Username / Judicial ID */}
              <div>
                <label
                  htmlFor="jf-username"
                  className="block text-xs font-semibold text-jf-ink uppercase tracking-wider mb-1.5"
                >
                  Username / Judicial ID
                </label>
                <div className="relative group">
                  <input
                    id="jf-username"
                    name="username"
                    type="text"
                    required
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder="e.g. registrar"
                    className="w-full h-12 pl-4 pr-11 text-sm text-jf-ink bg-jf-cream-darker/80 border border-jf-border-subtle rounded-input hover:border-jf-violet/40 hover:bg-jf-cream-darker focus:bg-jf-cream focus:border-jf-violet focus:ring-2 focus:ring-jf-violet/20 focus:shadow-[0_0_16px_rgba(80,78,118,0.12)] jf-focus-ring transition-all duration-150"
                  />
                  <div className="absolute inset-y-0 right-0 pr-3.5 flex items-center pointer-events-none text-jf-violet/40 group-focus-within:text-jf-violet transition-colors">
                    <User className="w-4 h-4" />
                  </div>
                </div>
              </div>

              {/* Password with Show/Hide Toggle */}
              <div>
                <label
                  htmlFor="jf-password"
                  className="block text-xs font-semibold text-jf-ink uppercase tracking-wider mb-1.5"
                >
                  Password
                </label>
                <div className="relative group">
                  <input
                    id="jf-password"
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    required
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    className="w-full h-12 pl-4 pr-11 text-sm text-jf-ink bg-jf-cream-darker/80 border border-jf-border-subtle rounded-input hover:border-jf-violet/40 hover:bg-jf-cream-darker focus:bg-jf-cream focus:border-jf-violet focus:ring-2 focus:ring-jf-violet/20 focus:shadow-[0_0_16px_rgba(80,78,118,0.12)] jf-focus-ring transition-all duration-150"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-jf-violet/50 hover:text-jf-violet focus:outline-none transition-colors"
                  >
                    <span className="p-1 rounded-md hover:bg-jf-violet/10 transition-colors">
                      {showPassword ? (
                        <EyeOff className="w-4 h-4" />
                      ) : (
                        <Eye className="w-4 h-4" />
                      )}
                    </span>
                  </button>
                </div>
              </div>

              {/* Primary Action Button */}
              <button
                type="submit"
                disabled={isLoading}
                aria-label="Sign in — Access Court Registry"
                className="group w-full h-12 flex justify-center items-center px-6 rounded-input text-sm font-semibold jf-btn-primary jf-focus-ring shadow-sm hover:shadow-[0_8px_25px_-4px_rgba(80,78,118,0.45),0_0_20px_rgba(241,100,46,0.22)] active:scale-[0.985] cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-150"
              >
                {isLoading ? (
                  <>
                    <span className="w-4 h-4 border-2 border-jf-cream/30 border-t-jf-cream rounded-full animate-spin mr-2.5" />
                    <span>Validating Authentication...</span>
                  </>
                ) : (
                  <>
                    <span>Sign in — Access Court Registry</span>
                    <ArrowRight className="ml-2.5 w-4 h-4 group-hover:translate-x-1.5 transition-transform duration-200" />
                  </>
                )}
              </button>
            </form>

            {/* Dev Quick-Fill Credentials (Gated to DEV mode) */}
            {(import.meta as any).env.DEV && (
              <div className="mt-8 pt-6 border-t border-jf-border-subtle animate-jf-fade-up" style={{ animationDelay: '180ms' }}>
                <div className="flex items-center gap-1.5 text-xs text-jf-ink-muted font-medium mb-3">
                  <Sparkles className="w-3.5 h-3.5 text-jf-violet" />
                  <span>Select Dev Role to Quick-Fill:</span>
                </div>
                <div className="grid grid-cols-3 gap-2.5">
                  <button
                    type="button"
                    onClick={() => handleQuickFill('registrar', 'registrar123')}
                    className="py-2 px-3 bg-jf-cream-darker hover:bg-jf-lavender/40 hover:border-jf-violet/60 hover:shadow-[0_4px_14px_rgba(80,78,118,0.18)] active:scale-95 text-jf-violet text-xs font-semibold rounded-lg border border-jf-border-subtle text-center jf-focus-ring hover:-translate-y-0.5 transition-all duration-150"
                  >
                    Registrar
                  </button>
                  <button
                    type="button"
                    onClick={() => handleQuickFill('judge', 'judge123')}
                    className="py-2 px-3 bg-jf-sage/15 hover:bg-jf-sage/25 hover:border-jf-sage hover:shadow-[0_4px_14px_rgba(163,181,101,0.28)] active:scale-95 text-jf-ink text-xs font-semibold rounded-lg border border-jf-sage/30 text-center jf-focus-ring hover:-translate-y-0.5 transition-all duration-150"
                  >
                    Judge
                  </button>
                  <button
                    type="button"
                    onClick={() => handleQuickFill('admin', 'admin123')}
                    className="py-2 px-3 bg-jf-apricot/30 hover:bg-jf-apricot/50 hover:border-jf-apricot-dark hover:shadow-[0_4px_14px_rgba(252,221,157,0.4)] active:scale-95 text-jf-ink text-xs font-semibold rounded-lg border border-jf-apricot text-center jf-focus-ring hover:-translate-y-0.5 transition-all duration-150"
                  >
                    Admin
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Institutional Compliance Footnote */}
          <div className="mt-8 pt-4 border-t border-jf-border-subtle/60 text-center sm:text-left space-y-1">
            <p className="text-[11px] text-jf-ink-muted leading-relaxed">
              Demo environment. All case data is synthetic, calibrated to NJDG aggregate statistics.
            </p>
            <p className="text-[10px] text-jf-ink-muted/80">
              Access is role-based: Admin, Registrar, Judge.
            </p>
          </div>
        </section>

        {/* =========================================================================
            RIGHT PANEL: Procedural Generative Art Canvas (~54% width on desktop)
            ========================================================================= */}
        <section
          aria-label="Explainable scheduling visual overview"
          className="w-full lg:w-[54%] relative overflow-hidden flex flex-col justify-between p-6 sm:p-10 lg:p-12 min-h-[220px] lg:min-h-[640px] bg-gradient-to-br from-jf-cream via-jf-apricot/20 to-jf-lavender/30 border-t lg:border-t-0 lg:border-l border-jf-border-subtle"
        >
          {/* Layer 1: Ambient Organic Multi-Gradient Mesh */}
          <div
            className="absolute inset-0 pointer-events-none opacity-80"
            style={{
              background: `
                radial-gradient(circle at 18% 22%, rgba(241, 100, 46, 0.22) 0%, transparent 45%),
                radial-gradient(circle at 82% 28%, rgba(252, 221, 157, 0.40) 0%, transparent 50%),
                radial-gradient(circle at 50% 75%, rgba(163, 181, 101, 0.28) 0%, transparent 55%),
                radial-gradient(circle at 85% 85%, rgba(196, 195, 227, 0.45) 0%, transparent 60%)
              `,
            }}
          />

          {/* Layer 2: Tactile brush underlay */}
          <div
            className="absolute inset-0 bg-cover bg-center pointer-events-none opacity-15 mix-blend-multiply"
            style={{ backgroundImage: `url(${bgBrush})` }}
            aria-hidden="true"
          />

          {/* Layer 3: SVG Contour & Stream Flow Lines */}
          <svg
            className="absolute inset-0 w-full h-full pointer-events-none opacity-35 mix-blend-multiply"
            viewBox="0 0 600 700"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            preserveAspectRatio="xMidYMid slice"
            aria-hidden="true"
          >
            <path
              d="M-50,150 C120,80 250,260 420,180 C520,130 580,210 650,190"
              stroke="var(--jf-orange)"
              strokeWidth="1.5"
              strokeDasharray="4 6"
            />
            <path
              d="M-40,260 C150,190 280,380 460,300 C550,250 600,320 680,290"
              stroke="var(--jf-violet)"
              strokeWidth="1.75"
            />
            <path
              d="M-20,380 C180,310 320,490 500,420 C580,380 620,440 700,410"
              stroke="var(--jf-sage)"
              strokeWidth="1.5"
            />
            <path
              d="M0,500 C200,430 350,600 530,540 C610,500 650,560 720,530"
              stroke="var(--jf-apricot)"
              strokeWidth="2"
            />
          </svg>

          {/* Layer 4: Procedural Film Grain Scrim */}
          <div
            className="absolute inset-0 pointer-events-none opacity-25 mix-blend-overlay"
            style={{
              backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`,
            }}
          />

          {/* Foreground Top Pill */}
          <div className="relative z-10 hidden sm:flex justify-end w-full animate-jf-fade-up">
            <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-jf-cream/85 backdrop-blur-md border border-jf-border-subtle shadow-xs hover:shadow-[0_4px_12px_rgba(80,78,118,0.12)] transition-shadow">
              <span className="flex items-center gap-1.5" aria-hidden="true">
                <span className="w-2 h-2 rounded-full bg-jf-violet" />
                <span className="w-2 h-2 rounded-full bg-jf-orange" />
                <span className="w-2 h-2 rounded-full bg-jf-sage" />
              </span>
              <span className="text-xs font-semibold text-jf-ink tracking-tight">
                Explainable by design
              </span>
            </div>
          </div>

          {/* Center: The Four Pillars of Explainable Scheduling */}
          <div className="relative z-10 hidden sm:flex flex-col justify-center my-auto py-2 w-full max-w-lg mx-auto animate-jf-fade-up" style={{ animationDelay: '80ms' }}>
            <div className="mb-4">
              <h2 className="text-xl sm:text-2xl font-bold tracking-tight text-jf-ink">
                Fairness &amp; Explainability by Design
              </h2>
              <div className="inline-flex items-center gap-2 px-2.5 py-0.5 rounded-md bg-jf-cream/80 border border-jf-border-subtle text-[10px] font-semibold tracking-wider text-jf-ink-muted my-1.5">
                <span>Algorithmic Listing Protocol</span>
                <span className="w-1 h-1 rounded-full bg-jf-orange" />
                <span>NJDG Calibrated</span>
              </div>
              <p className="text-xs text-jf-ink-muted leading-relaxed">
                Multi-criteria mathematical optimization eliminates scheduling bottlenecks while protecting judicial discretion.
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="group p-3.5 rounded-xl bg-jf-cream/90 backdrop-blur-md border border-jf-border-subtle shadow-card-soft hover:-translate-y-0.5 hover:border-jf-violet/50 hover:shadow-[0_8px_24px_-4px_rgba(80,78,118,0.22)] transition-all duration-200 cursor-default">
                <div className="w-7 h-7 rounded-lg bg-jf-violet/10 text-jf-violet flex items-center justify-center mb-2 group-hover:scale-105 transition-transform">
                  <Scale className="w-4 h-4" />
                </div>
                <div className="text-xs font-bold text-jf-ink">Statutory Priority</div>
                <p className="text-[11px] text-jf-ink-muted leading-snug mt-1">
                  Automated fast-tracking for Bail, POCSO, and urgent liberty petitions.
                </p>
              </div>

              <div className="group p-3.5 rounded-xl bg-jf-cream/90 backdrop-blur-md border border-jf-border-subtle shadow-card-soft hover:-translate-y-0.5 hover:border-jf-orange/50 hover:shadow-[0_8px_24px_-4px_rgba(241,100,46,0.25)] transition-all duration-200 cursor-default">
                <div className="w-7 h-7 rounded-lg bg-jf-orange/15 text-jf-text-orange flex items-center justify-center mb-2 group-hover:scale-105 transition-transform">
                  <Clock className="w-4 h-4" />
                </div>
                <div className="text-xs font-bold text-jf-ink">Anti-Starvation</div>
                <p className="text-[11px] text-jf-ink-muted leading-snug mt-1">
                  Calibrated aging progression ensures ordinary civil filings are never neglected.
                </p>
              </div>

              <div className="group p-3.5 rounded-xl bg-jf-cream/90 backdrop-blur-md border border-jf-border-subtle shadow-card-soft hover:-translate-y-0.5 hover:border-jf-sage/60 hover:shadow-[0_8px_24px_-4px_rgba(163,181,101,0.28)] transition-all duration-200 cursor-default">
                <div className="w-7 h-7 rounded-lg bg-jf-sage/20 text-jf-ink flex items-center justify-center mb-2 group-hover:scale-105 transition-transform">
                  <Building2 className="w-4 h-4" />
                </div>
                <div className="text-xs font-bold text-jf-ink">Capacity Protection</div>
                <p className="text-[11px] text-jf-ink-muted leading-snug mt-1">
                  Strict constraints eliminate judge double-booking and room overload.
                </p>
              </div>

              <div className="group p-3.5 rounded-xl bg-jf-cream/90 backdrop-blur-md border border-jf-border-subtle shadow-card-soft hover:-translate-y-0.5 hover:border-jf-apricot hover:shadow-[0_8px_24px_-4px_rgba(252,221,157,0.38)] transition-all duration-200 cursor-default">
                <div className="w-7 h-7 rounded-lg bg-jf-apricot/40 text-jf-ink flex items-center justify-center mb-2 group-hover:scale-105 transition-transform">
                  <ShieldCheck className="w-4 h-4" />
                </div>
                <div className="text-xs font-bold text-jf-ink">Immutable Audit</div>
                <p className="text-[11px] text-jf-ink-muted leading-snug mt-1">
                  Every automated draft listing and registrar override is cryptographically logged.
                </p>
              </div>
            </div>
          </div>

          {/* Bottom: Institutional Principles Carousel */}
          <div className="relative z-10 hidden sm:block w-full animate-jf-fade-up" style={{ animationDelay: '140ms' }}>
            <PrinciplesCarousel />
          </div>
        </section>
      </main>
    </div>
  );
};
