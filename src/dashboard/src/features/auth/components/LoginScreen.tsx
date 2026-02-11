import { Moon } from 'lucide-react';
import { useTranslation } from '@/lib/i18n';

interface LoginScreenProps {
  onLogin: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({ onLogin }) => {
  const { t } = useTranslation();

  return (
    <div className="min-h-screen relative flex flex-col items-center justify-center p-6 overflow-hidden">
      {/* Background Image with Overlay */}
      <div
        className="absolute inset-0 bg-cover bg-center bg-no-repeat"
        style={{
          backgroundImage: `url('https://lh3.googleusercontent.com/aida-public/AB6AXuBhD8TVdVrrfUzTcF36I3fmq8fNGFHoPDB-P7-tX-uy3zJx3WFjryQlbPwnkWmp_CttqincS55X7H2x4YRn78Psan1RGLnNqC5-HBh0pEGUYxq6dsGzDxT_POaBr_I3KErOn66hUcCsjTklTZ3DKlZyR9-OLt68YxsPMaWzea5b1loqebW5uAE6gheHsDstyr2BMiCICvVt89-J0we_zM_iStQ9i6VaLiSkCMRxZb67mw1zrPcjYbIoffixa5a40b2SZv-lHba8uK8')`,
        }}
      >
        {/* Dark Vignette and Bottom Gradient */}
        <div className="absolute inset-0 bg-black/50" />
        <div className="absolute inset-0 bg-gradient-to-b from-black/40 via-transparent to-[#0A0A0B]" />
        <div className="absolute inset-0 bg-radial-gradient from-transparent to-black/60" />
      </div>

      {/* Login Card Container */}
      <div className="relative z-10 w-full max-w-[420px]">
        {/* Main Card */}
        <div className="w-full bg-[#111827] border border-white/10 rounded-2xl p-10 flex flex-col items-center shadow-[0_0_50px_-12px_rgba(0,0,0,0.5)]">
          {/* Mascot Icon with Glow (Inside) */}
          <div className="relative mb-8">
            {/* Radial Glow Aura */}
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-32 h-32 bg-indigo-500/30 rounded-full blur-[40px]" />

            <div className="relative w-20 h-20 bg-[#111827] rounded-full border border-white/10 flex items-center justify-center shadow-2xl">
              <Moon className="w-10 h-10 text-white" />
            </div>
          </div>

          <h1 className="text-3xl font-bold text-white mb-2 tracking-tight">{t('login.title')}</h1>
          <p className="text-[#9CA3AF] text-base text-center mb-10 leading-relaxed font-normal">
            {t('login.subtitle')}
          </p>

          <div className="w-full space-y-6">
            <button
              onClick={onLogin}
              className="w-full bg-[#5865F2] hover:bg-[#4752C4] active:scale-[0.98] text-white font-semibold py-4 px-6 rounded-lg transition-all duration-300 flex items-center justify-center gap-3 shadow-lg shadow-[#5865F2]/20 group"
            >
              <svg className="w-6 h-6 fill-current" viewBox="0 0 127.14 96.36">
                <path d="M107.7,8.07A105.15,105.15,0,0,0,81.47,0a72.06,72.06,0,0,0-3.36,6.83A97.68,97.68,0,0,0,49,6.83,72.37,72.37,0,0,0,45.64,0,105.89,105.89,0,0,0,19.39,8.09C2.79,32.65-1.71,56.6.54,80.21h0A105.73,105.73,0,0,0,32.71,96.36,77.11,77.11,0,0,0,39.6,85.25a68.42,68.42,0,0,1-10.85-5.18c.91-.66,1.8-1.34,2.66-2a75.57,75.57,0,0,0,64.32,0c.87.71,1.76,1.39,2.66,2a68.68,68.68,0,0,1-10.87,5.19,77,77,0,0,0,6.89,11.1A105.89,105.89,0,0,0,126.6,80.22c1.24-21.6-3.79-45.2-18.9-72.15ZM42.45,65.69C36.18,65.69,31,60,31,53s5-12.74,11.43-12.74S54,46,53.89,53,48.84,65.69,42.45,65.69Zm42.24,0C78.41,65.69,73.25,60,73.25,53s5-12.74,11.44-12.74S96.23,46,96.12,53,91.08,65.69,84.69,65.69Z" />
              </svg>
              {t('login.loginButton')}
            </button>

            <div className="text-center">
              <p className="text-[12px] text-[#6B7280] leading-normal font-normal">
                {t('login.restriction')}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Footer Links */}
      <div className="fixed bottom-8 w-full z-10">
        <div className="flex items-center justify-center gap-4 text-[12px] text-[#4B5563] font-medium tracking-wide">
          <a href="#" className="hover:text-white transition-colors uppercase tracking-widest">
            {t('login.privacy')}
          </a>
          <span className="opacity-30">•</span>
          <a href="#" className="hover:text-white transition-colors uppercase tracking-widest">
            {t('login.terms')}
          </a>
          <span className="opacity-30">•</span>
          <a href="#" className="hover:text-white transition-colors uppercase tracking-widest">
            {t('login.support')}
          </a>
        </div>
      </div>
    </div>
  );
};
