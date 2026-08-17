import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "IntelligentCalls — Tableau de bord",
  description: "CRM intelligent avec transcription IA et chatbot basé sur vos contacts",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
