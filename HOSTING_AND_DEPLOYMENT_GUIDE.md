# 🌐 Guide Complet d'Hébergement, Déploiement & Architecture des Données
## Intelligent Calls — Mise en Production, Schémas de Base de Données & Flux Réseau

---

## 📑 Sommaire

1. [Vue d'Ensemble de l'Infrastructure & Topologie Réseau](#1-vue-densemble-de-linfrastructure--topologie-réseau)
2. [Où se Trouvent les Données ? (Détails des Bases de Données)](#2-où-se-trouvent-les-données--détails-des-bases-de-données)
   - [2.1 Base de Données Serveur (MySQL / PostgreSQL / SQLite)](#21-base-de-données-serveur-mysql--postgresql--sqlite)
   - [2.2 Schéma Détaillé des Tables Backend (SQLAlchemy)](#22-schéma-détaillé-des-tables-backend-sqlalchemy)
   - [2.3 Base de Données Locale Android (SQLite)](#23-base-de-données-locale-android-sqlite)
3. [Flux de Données de Bout en Bout (Dataflow)](#3-flux-de-données-de-bout-en-bout-dataflow)
4. [Que Faut-il Modifier pour Remplacer `localhost` ?](#4-que-faut-il-modifier-pour-remplacer-localhost-)
5. [Options d'Hébergement & Déploiement du Serveur Backend](#5-options-dhébergement--déploiement-du-serveur-backend)
   - [Option A : Hébergement Cloud Gratuit / Clé en Main (Railway / Render / Fly.io)](#option-a--hébergement-cloud-gratuit--clé-en-main-railway--render--flyio)
   - [Option B : Serveur VPS Dédié (Ubuntu 22.04 / 24.04 + Docker + Nginx SSL)](#option-b--serveur-vps-dédié-ubuntu-2204--2404--docker--nginx-ssl)
   - [Option C : Tunnel Sécurisé Gratuit depuis votre PC (Cloudflare Tunnel / Ngrok)](#option-c--tunnel-sécurisé-gratuit-depuis-votre-pc-cloudflare-tunnel--ngrok)
6. [Déploiement du Dashboard Web (Next.js 16) sur Vercel](#6-déploiement-du-dashboard-web-nextjs-16-sur-vercel)
7. [Fichier de Variables d'Environnement de Production (`.env`)](#7-fichier-de-variables-denvironnement-de-production-env)

---

## 1. Vue d'Ensemble de l'Infrastructure & Topologie Réseau

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                    TOPOLOGIE GLOBALE                                   │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   [Smartphone Android]               [Dashboard Web Next.js]                           │
│   (En mobilité 4G/5G/Wi-Fi)          (Navigateur Chrome/Safari)                        │
│            │                                    │                                      │
│            │ HTTPS / WSS                        │ HTTPS (Vercel)                       │
│            ▼                                    ▼                                      │
│   ┌─────────────────────────────────────────────────────────┐                          │
│   │              REVERSE PROXY NGINX / CLOUDFLARE           │                          │
│   │              (SSL / HTTPS / Port 443 / WSS)             │                          │
│   └────────────────────────────┬────────────────────────────┘                          │
│                                │ Reverse Proxy (Port 8000)                             │
│                                ▼                                                       │
│   ┌─────────────────────────────────────────────────────────┐                          │
│   │             BACKEND FASTAPI (Python 3.12)               │                          │
│   │             (Uvicorn Worker / Asynchrone)               │                          │
│   └───────────────┬─────────────────────────┬───────────────┘                          │
│                   │                         │                                          │
│                   ▼                         ▼                                          │
│   ┌───────────────────────────────┐ ┌────────────────────────────────┐                 │
│   │   BASE DE DONNÉES MYSQL       │ │      GROQ INFERENCE CLOUD      │                 │
│   │   (appcall_db / Port 3306)    │ │   - Whisper Large v3 Turbo     │                 │
│   │   - Users & Contacts          │ │   - GPT-OSS 120B / LLaMA 3.3   │                 │
│   │   - Calls, Transcripts, RDV   │ └────────────────────────────────┘                 │
│   └───────────────────────────────┘                                                    │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Où se Trouvent les Données ? (Détails des Bases de Données)

Le système utilise **deux niveaux de bases de données synchronisées** :

### 2.1 Base de Données Serveur (MySQL / PostgreSQL / SQLite)
- **Localisation** : Sur le serveur hébergeant le backend FastAPI (par défaut MySQL sur le port `3306`, base `appcall_db`).
- **Configuration** : Définie dans le fichier `backend/.env` via la variable `DATABASE_URL`.
  - Format MySQL : `mysql+pymysql://<user>:<password>@<host>:3306/appcall_db`
  - Format PostgreSQL : `postgresql://<user>:<password>@<host>:5432/appcall_db`
  - Format SQLite (fichier unique sans serveur) : `sqlite:///./appcall_db.sqlite3`

### 2.2 Schéma Détaillé des Tables Backend (SQLAlchemy)

```mermaid
erDiagram
    users ||--o{ calls : passes
    users ||--o{ contacts : gere
    users ||--o{ tasks : assigne
    users ||--o{ agenda_items : organise
    users ||--o{ files : televerse
    users ||--o{ reminders : configure
    users ||--o{ audit_logs : declenche

    contacts ||--o{ calls : est_associe
    calls ||--o| transcripts : possede
    calls ||--o| call_summaries : genere
    call_summaries ||--o| appointments : extrait

    users {
        VARCHAR(36) id PK "UUID"
        VARCHAR(255) email UK "Email de connexion"
        VARCHAR(255) hashed_password "Bcrypt hash"
        VARCHAR(255) full_name "Nom complet"
        BOOLEAN is_active "Compte actif"
        BOOLEAN is_superuser "Admin"
        BOOLEAN gdpr_voice_consent "Consentement vocal global"
        DATETIME created_at "Horodatage création"
        DATETIME updated_at "Horodatage modification"
    }

    contacts {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) user_id FK "Lien utilisateur"
        VARCHAR(100) first_name "Prénom"
        VARCHAR(100) last_name "Nom"
        VARCHAR(50) phone_number "Numéro E.164"
        VARCHAR(255) email "Email"
        VARCHAR(255) company "Entreprise"
        BOOLEAN gdpr_consent "Consentement contact"
        DATETIME gdpr_consent_date "Date consentement"
    }

    calls {
        VARCHAR(36) id PK "UUID ou ID natif (native-timestamp)"
        VARCHAR(36) user_id FK "Lien utilisateur"
        VARCHAR(36) contact_id FK "Lien contact"
        VARCHAR(20) direction "INBOUND ou OUTBOUND"
        DATETIME started_at "Date et heure de début"
        DATETIME ended_at "Date et heure de fin"
        VARCHAR(20) status "INITIATED, RECORDING, COMPLETED"
        BOOLEAN consent_given "Consentement d'enregistrement"
        DATETIME consent_timestamp "Horodatage du consentement"
        JSON twilio_params "Métadonnées d'appel (numéro, nom)"
        VARCHAR(512) audio_url "URL ou chemin local de l'audio"
        VARCHAR(20) ai_status "PENDING, PROCESSING, DONE, FAILED"
    }

    transcripts {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) call_id FK "Lien appel (Unique)"
        LONGTEXT raw_text "Texte complet transcrit par Whisper"
        VARCHAR(10) language "Code langue (fr, en...)"
        FLOAT confidence_score "Score de confiance (ex: 98.5%)"
        JSON speaker_segments "Tableau des segments horodatés et diarisés"
        DATETIME created_at "Date transcription"
    }

    call_summaries {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) call_id FK "Lien appel (Unique)"
        LONGTEXT summary_text "Résumé IA synthétique et contextuel"
        VARCHAR(20) status "PROPOSED, VALIDATED, MODIFIED, CONFIRMED"
        FLOAT confidence_score "Score de confiance du résumé"
        VARCHAR(36) detected_appointment_id "ID du RDV détecté associé"
        DATETIME created_at "Date de génération"
    }

    appointments {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) call_summary_id FK "Lien résumé d'appel"
        VARCHAR(255) title "Objet du rendez-vous (ex: Signature contrat)"
        DATETIME scheduled_at "Date et heure ISO-8601 du RDV"
        VARCHAR(20) status "PROPOSED, CONFIRMED, CANCELLED, DISMISSED"
        VARCHAR(255) contact_name "Nom de l'interlocuteur"
        VARCHAR(50) phone_number "Numéro de téléphone"
        TEXT summary_context "Extrait contextuel de la discussion"
    }

    tasks {
        VARCHAR(36) id PK "UUID ou task_id local"
        VARCHAR(36) user_id FK "Lien utilisateur"
        VARCHAR(255) title "Description de la tâche"
        BOOLEAN completed "Statut de complétion (0 ou 1)"
        DATETIME due_date "Date d'échéance optionnelle"
        VARCHAR(20) priority "LOW, MEDIUM, HIGH"
    }

    agenda_items {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) user_id FK "Lien utilisateur"
        VARCHAR(255) title "Titre de l'événement"
        DATETIME scheduled_at "Date et heure de l'événement"
        VARCHAR(255) contact_name "Nom du contact"
        VARCHAR(50) phone_number "Numéro associé"
        VARCHAR(20) status "CONFIRMED, TENTATIVE"
    }

    files {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) user_id FK "Lien utilisateur"
        VARCHAR(255) filename "Nom original du fichier"
        VARCHAR(512) stored_path "Chemin sur le disque serveur"
        BIGINT file_size "Taille en octets"
        VARCHAR(100) mime_type "Type MIME"
    }

    audit_logs {
        VARCHAR(36) id PK "UUID"
        VARCHAR(36) user_id FK "Lien utilisateur"
        VARCHAR(100) action "Type d'action (LOGIN, EXPORT, PURGE...)"
        VARCHAR(50) ip_address "Adresse IP"
        TEXT details "Détails de l'action"
        DATETIME timestamp "Horodatage"
    }
```

### 2.3 Base de Données Locale Android (SQLite)
- **Localisation** : Dans la mémoire interne de l'application Android : `/data/data/com.example.appcall/databases/appcall_local.db`.
- **Rôle** : Fournir l'affichage instantané sans latence, le fonctionnement hors-ligne (mode avion) et la file d'attente d'upload `sync_queue`.

---

## 3. Flux de Données de Bout en Bout (Dataflow)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Contact Téléphonique
    actor User as Commercial / Utilisateur Android
    participant App as Application Android
    participant SQLite as SQLite Local (appcall_local.db)
    participant Backend as Serveur FastAPI
    participant DB as Base MySQL (appcall_db)
    participant Groq as Moteur IA Groq Cloud

    User->>Client: Appel téléphonique PSTN
    App->>App: Interception & Enregistrement Audio (.m4a)
    User->>Client: Fin de l'appel (Raccrochage)
    
    App->>SQLite: Enregistrement immédiat (Statut: PENDING)
    App->>Backend: POST /api/v1/calls/{id}/audio (Fichier + Headers Contact)
    
    Backend->>DB: Sauvegarde de l'appel (ai_status = PROCESSING)
    Backend->>Groq: Audio Stream ➔ Whisper Large v3 Turbo
    Groq-->>Backend: Transcription textuelle + Segments diarisés
    Backend->>DB: INSERT INTO transcripts (raw_text, speaker_segments)
    
    Backend->>Groq: Prompt Transcription ➔ LLM Cascade (GPT-OSS 120B / LLaMA 3.3)
    Groq-->>Backend: JSON Structuré (Résumé + Rendez-vous détecté)
    Backend->>DB: INSERT INTO call_summaries & appointments
    Backend->>DB: UPDATE calls SET ai_status = 'DONE'
    
    App->>Backend: Polling GET /api/v1/calls/{id}/summary
    Backend-->>App: 200 OK (Résumé + Transcription + RDV)
    App->>SQLite: Mise à jour du cache local
    App->>User: Affichage du résumé, bulles de dialogue et bouton validation RDV
```

---

## 4. Que Faut-il Modifier pour Remplacer `localhost` ?

Par défaut, l'application est configurée pour pointer vers `http://127.0.0.1:8000` (pour émulateur) ou `http://192.168.1.12:8000` (pour réseau local Wi-Fi).

### 📱 1. Dans l'Application Android (AUCUNE RECOMPILATION NÉCESSAIRE !)
L'application intègre un intercepteur dynamique d'URL ([DynamicUrlInterceptor.kt](file:///c:/Users/khali/OneDrive/Bureau/intelligentCall/IntelligentCalls/app/src/main/java/com/example/appcall/data/api/DynamicUrlInterceptor.kt)) :

1. Ouvrez l'application Android sur votre téléphone.
2. Sur l'écran de **Connexion (Login)** ou dans la section **Paramètres (Settings)** :
   - Cliquez sur **"Changer l'adresse du serveur"** (ou le champ d'URL du serveur).
   - Entrez simplement votre adresse d'hébergement publique (ex: `https://api.monserveur.com` ou `https://mon-app.up.railway.app`).
   - Appuyez sur **Enregistrer**.
3. **Toutes les requêtes Retrofit, d'upload audio et de synchronisation basculent instantanément vers cette adresse !**

### ⚙️ 2. Dans le Backend (`backend/.env`)
Mettez à jour le fichier `.env` sur le serveur :
```env
# URL publique de votre serveur (utilisée pour les webhooks Twilio/Vonage et liens audio)
SERVER_BASE_URL=https://api.monserveur.com

# Hôte d'écoute (0.0.0.0 pour accepter les connexions externes)
SERVER_HOST=0.0.0.0
SERVER_PORT=8000

# Autoriser les origines CORS (Dashboard Web et Mobile)
CORS_ORIGINS=http://localhost:3000,https://dashboard.monserveur.com,https://mon-app.vercel.app

# Connexion à la base de données de production
DATABASE_URL=mysql+pymysql://admin_calls:MonSuperMotDePasse@localhost:3306/appcall_db

# Clé API Groq Cloud (Production)
GROQ_API_KEY=gsk_votre_cle_groq_reelle
```

### 💻 3. Dans le Dashboard Web (`dashboard/.env.local`)
```env
NEXT_PUBLIC_API_URL=https://api.monserveur.com
```

---

## 5. Options d'Hébergement & Déploiement du Serveur Backend

---

### Option A : Hébergement Cloud Gratuit / Clé en Main (Railway / Render / Fly.io)
> 💡 **Idéal pour une mise en ligne en 5 minutes sans gérer de serveur Linux, avec HTTPS automatique inclus.**

#### Déploiement sur Railway :
1. Créez un compte sur [railway.app](https://railway.app).
2. Cliquez sur **"New Project"** $\rightarrow$ **"Deploy from GitHub repo"** $\rightarrow$ Sélectionnez `IntelligentAppCalls`.
3. Définissez le **Root Directory** sur `/backend`.
4. Ajoutez un plugin **MySQL** (ou **PostgreSQL**) dans le tableau de bord Railway.
5. Dans l'onglet **Variables**, ajoutez :
   - `DATABASE_URL` = `${{MySQL.DATABASE_URL}}`
   - `GROQ_API_KEY` = `gsk_...`
   - `JWT_SECRET` = `une_cle_secrete_aleatoire_tres_longue`
   - `CORS_ORIGINS` = `*`
6. Railway génère une URL publique HTTPS (ex : `https://intelligent-calls-production.up.railway.app`).
7. **Copiez cette URL et collez-la dans l'application Android !**

---

### Option B : Serveur VPS Dédié (Ubuntu 22.04 / 24.04 + Docker + Nginx SSL)
> 🛡️ **Idéal pour la souveraineté des données, conformité RGPD européenne et performances maximales (Hetzner, OVH, DigitalOcean, AWS EC2).**

#### 1. Configuration Initiale du Serveur Ubuntu
```bash
# Mettre à jour le système
sudo apt update && sudo apt upgrade -y
sudo apt install -y python3-pip python3-venv git nginx certbot python3-certbot-nginx mysql-server

# Sécuriser MySQL
sudo mysql_secure_installation
```

#### 2. Création de la Base de Données MySQL
```sql
sudo mysql -u root -p
CREATE DATABASE appcall_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'appcall_user'@'localhost' IDENTIFIED BY 'MotDePasseTresSecurise2026!';
GRANT ALL PRIVILEGES ON appcall_db.* TO 'appcall_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

#### 3. Clonage & Installation du Backend
```bash
cd /var/www
sudo git clone https://github.com/Ysn-Ir/IntelligentAppCalls.git
cd IntelligentAppCalls/backend

# Créer l'environnement virtuel Python
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Créer le fichier de configuration .env
cp .env.example .env
nano .env
```
*(Renseignez votre `DATABASE_URL=mysql+pymysql://appcall_user:MotDePasseTresSecurise2026!@localhost:3306/appcall_db` et votre `GROQ_API_KEY`)*.

#### 4. Configuration du Service Systemd (Redémarrage Automatique)
Créez le fichier `/etc/systemd/system/intelligent-calls.service` :
```ini
[Unit]
Description=Intelligent Calls FastAPI Backend
After=network.target mysql.service

[Service]
User=root
WorkingDirectory=/var/www/IntelligentAppCalls/backend
ExecStart=/var/www/IntelligentAppCalls/backend/venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000 --workers 4
Restart=always
RestartSec=5
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
```
Activez et démarrez le service :
```bash
sudo systemctl daemon-reload
sudo systemctl enable intelligent-calls
sudo systemctl start intelligent-calls
sudo systemctl status intelligent-calls
```

#### 5. Configuration Nginx & Certificat SSL Gratuit Let's Encrypt
Créez le fichier `/etc/nginx/sites-available/api.votredomaine.com` :
```nginx
server {
    server_name api.votredomaine.com;

    client_max_body_size 50M;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```
Activez le site et générez le certificat SSL HTTPS :
```bash
sudo ln -s /etc/nginx/sites-available/api.votredomaine.com /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d api.votredomaine.com
```

---

### Option C : Tunnel Sécurisé Gratuit depuis votre PC (Cloudflare Tunnel / Ngrok)
> 🚀 **Idéal pour tester immédiatement l'application sur un vrai téléphone en 4G sans payer de serveur ni ouvrir de ports sur votre box Internet.**

#### Utilisation de Cloudflare Tunnel (100% Gratuit & Illimité) :
1. Téléchargez `cloudflared` pour Windows :
   ```powershell
   winget install Cloudflare.cloudflared
   ```
2. Lancez le tunnel directement sur le port de votre backend (8000) :
   ```powershell
   cloudflared tunnel --url http://localhost:8000
   ```
3. Cloudflare vous fournit instantanément une URL HTTPS publique sécurisée du type :
   `https://random-words-1234.trycloudflare.com`
4. **Tapez cette URL dans votre application Android : vos appels en 4G/5G se synchroniseront immédiatement avec votre PC !**

---

## 6. Déploiement du Dashboard Web (Next.js 16) sur Vercel

1. Créez un compte sur [vercel.com](https://vercel.com).
2. Importez votre dépôt GitHub `IntelligentAppCalls`.
3. Configurez le **Root Directory** sur `dashboard`.
4. Ajoutez la variable d'environnement :
   - `NEXT_PUBLIC_API_URL` = `https://api.votredomaine.com` (ou l'URL Railway/Cloudflare)
5. Cliquez sur **Deploy**. Votre dashboard CRM web est en ligne et connecté au backend.

---

## 7. Fichier de Variables d'Environnement de Production (`.env`)

```env
# ==============================================================================
# CONFIGURATION SERVEUR ET API
# ==============================================================================
ENVIRONMENT=production
SERVER_BASE_URL=https://api.votredomaine.com
SERVER_HOST=0.0.0.0
SERVER_PORT=8000
CORS_ORIGINS=https://dashboard.votredomaine.com,http://localhost:3000

# ==============================================================================
# BASE DE DONNÉES MYSQL DE PRODUCTION
# ==============================================================================
DATABASE_URL=mysql+pymysql://appcall_user:MotDePasseTresSecurise2026!@localhost:3306/appcall_db

# ==============================================================================
# AUTHENTIFICATION & SÉCURITÉ JWT
# ==============================================================================
JWT_SECRET=super_cle_secrete_production_a_remplacer_par_une_chaine_aleatoire_64_bits
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=43200

# ==============================================================================
# MOTEURS IA CLOUD GROQ (STT WHISPER & LLM CASCADE)
# ==============================================================================
GROQ_API_KEY=gsk_votre_cle_groq_production
GROQ_BASE_URL=https://api.groq.com/openai/v1
GROQ_MODEL=openai/gpt-oss-120b
GROQ_FALLBACK_MODEL=llama-3.3-70b-versatile
GROQ_STT_MODEL=whisper-large-v3-turbo

# ==============================================================================
# STOCKAGE AUDIO & RGPD
# ==============================================================================
AUDIO_UPLOAD_DIR=./uploads
GDPR_DATA_RETENTION_DAYS=365
```
