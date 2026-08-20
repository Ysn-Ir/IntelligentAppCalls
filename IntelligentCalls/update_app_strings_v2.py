# Script to generate AppStrings.kt with exhaustive localization
import re

file_path = r"c:\Users\khali\OneDrive\Bureau\intelligentCall\IntelligentCalls\app\src\main\java\com\example\appcall\presentation\theme\AppStrings.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Fields to add to data class TranslationStrings
new_fields = """    val voiceDictationNotAvailable: String,
    val firstNameLabel: String,
    val lastNameLabel: String,
    val emailLabel: String,
    val phoneLabel: String,
    val oldPasswordLabel: String,
    val newPasswordLabel: String,
    val confirmPasswordLabel: String,
    val passwordEmptyError: String,
    val passwordMismatchError: String,
    val passwordUpdatedSuccess: String,
    val profileUpdatedSuccess: String,
    val aiWelcomeMessage: String,
    val aiNewConversationStarted: String"""

content = content.replace("    val voiceDictationNotAvailable: String", new_fields)

# English
en_add = """    voiceDictationNotAvailable = "Voice dictation not available on this device",
    firstNameLabel = "First Name",
    lastNameLabel = "Last Name",
    emailLabel = "Email",
    phoneLabel = "Phone Number",
    oldPasswordLabel = "Old Password",
    newPasswordLabel = "New Password",
    confirmPasswordLabel = "Confirm Password",
    passwordEmptyError = "Password cannot be empty",
    passwordMismatchError = "Passwords do not match",
    passwordUpdatedSuccess = "Password updated successfully",
    profileUpdatedSuccess = "Profile updated successfully",
    aiWelcomeMessage = "Hello! I am your AI assistant. You can ask me questions across all your calls or target a specific contact.",
    aiNewConversationStarted = "New conversation started! Ask a question about your calls."
)"""
content = re.sub(r'voiceDictationNotAvailable = "Voice dictation not available on this device"\s*\)', en_add, content)

# French
fr_add = """    voiceDictationNotAvailable = "Dictée vocale non disponible sur ce terminal",
    firstNameLabel = "Prénom",
    lastNameLabel = "Nom",
    emailLabel = "Email",
    phoneLabel = "Numéro de téléphone",
    oldPasswordLabel = "Ancien mot de passe",
    newPasswordLabel = "Nouveau mot de passe",
    confirmPasswordLabel = "Confirmer le mot de passe",
    passwordEmptyError = "Le mot de passe ne peut pas être vide",
    passwordMismatchError = "Les mots de passe ne correspondent pas",
    passwordUpdatedSuccess = "Mot de passe modifié avec succès",
    profileUpdatedSuccess = "Profil mis à jour",
    aiWelcomeMessage = "Bonjour ! Je suis votre assistant IA. Vous pouvez me poser des questions sur l'ensemble de vos appels ou cibler un contact spécifique.",
    aiNewConversationStarted = "Nouvelle conversation démarrée ! Posez votre question sur l'ensemble de vos appels."
)"""
content = re.sub(r'voiceDictationNotAvailable = "Dictée vocale non disponible sur ce terminal"\s*\)', fr_add, content)

# Arabic
ar_add = """    voiceDictationNotAvailable = "الإملاء الصوتي غير متاح على هذا الجهاز",
    firstNameLabel = "الاسم الأول",
    lastNameLabel = "اسم العائلة",
    emailLabel = "البريد الإلكتروني",
    phoneLabel = "رقم الهاتف",
    oldPasswordLabel = "كلمة المرور القديمة",
    newPasswordLabel = "كلمة المرور الجديدة",
    confirmPasswordLabel = "تأكيد كلمة المرور",
    passwordEmptyError = "لا يمكن أن تكون كلمة المرور فارغة",
    passwordMismatchError = "كلمات المرور غير متطابقة",
    passwordUpdatedSuccess = "تم تعديل كلمة المرور بنجاح",
    profileUpdatedSuccess = "تم تحديث الملف الشخصي",
    aiWelcomeMessage = "مرحباً! أنا مساعدك الذكي. يمكنك طرح أسئلة حول جميع مكالماتك أو تحديد جهة اتصال معينة.",
    aiNewConversationStarted = "بدأت محادثة جديدة! اطرح سؤالك حول مكالماتك."
)"""
content = re.sub(r'voiceDictationNotAvailable = "الإملاء الصوتي غير متاح على هذا الجهاز"\s*\)', ar_add, content)

# Spanish
es_add = """    voiceDictationNotAvailable = "Dictado por voz no disponible en este dispositivo",
    firstNameLabel = "Nombre",
    lastNameLabel = "Apellido",
    emailLabel = "Correo electrónico",
    phoneLabel = "Número de teléfono",
    oldPasswordLabel = "Contraseña anterior",
    newPasswordLabel = "Nueva contraseña",
    confirmPasswordLabel = "Confirmar contraseña",
    passwordEmptyError = "La contraseña no puede estar vacía",
    passwordMismatchError = "Las contraseñas no coinciden",
    passwordUpdatedSuccess = "Contraseña modificada con éxito",
    profileUpdatedSuccess = "Perfil actualizado",
    aiWelcomeMessage = "¡Hola! Soy tu asistente de IA. Puedes hacerme preguntas sobre todas tus llamadas o seleccionar un contacto específico.",
    aiNewConversationStarted = "¡Nueva conversación iniciada! Haz tu pregunta sobre tus llamadas."
)"""
content = re.sub(r'voiceDictationNotAvailable = "Dictado por voz no disponible en este dispositivo"\s*\)', es_add, content)

# German
de_add = """    voiceDictationNotAvailable = "Sprachdiktat auf diesem Gerät nicht verfügbar",
    firstNameLabel = "Vorname",
    lastNameLabel = "Nachname",
    emailLabel = "E-Mail",
    phoneLabel = "Telefonnummer",
    oldPasswordLabel = "Altes Passwort",
    newPasswordLabel = "Neues Passwort",
    confirmPasswordLabel = "Passwort bestätigen",
    passwordEmptyError = "Passwort darf nicht leer sein",
    passwordMismatchError = "Passwörter stimmen nicht überein",
    passwordUpdatedSuccess = "Passwort erfolgreich geändert",
    profileUpdatedSuccess = "Profil aktualisiert",
    aiWelcomeMessage = "Hallo! Ich bin Ihr KI-Assistent. Sie können mir Fragen zu allen Ihren Anrufen stellen oder einen bestimmten Kontakt auswählen.",
    aiNewConversationStarted = "Neues Gespräch gestartet! Stellen Sie Ihre Frage zu Ihren Anrufen."
)"""
content = re.sub(r'voiceDictationNotAvailable = "Sprachdiktat auf diesem Gerät nicht verfügbar"\s*\)', de_add, content)

# Chinese
zh_add = """    voiceDictationNotAvailable = "此设备不支持语音听写",
    firstNameLabel = "名字",
    lastNameLabel = "姓氏",
    emailLabel = "电子邮件",
    phoneLabel = "电话号码",
    oldPasswordLabel = "旧密码",
    newPasswordLabel = "新密码",
    confirmPasswordLabel = "确认密码",
    passwordEmptyError = "密码不能为空",
    passwordMismatchError = "密码不一致",
    passwordUpdatedSuccess = "密码修改成功",
    profileUpdatedSuccess = "个人资料已更新",
    aiWelcomeMessage = "您好！我是您的AI助手。您可以向我询问所有通话的详情或指定联系人。",
    aiNewConversationStarted = "新对话已开启！请输入有关通话的问题。"
)"""
content = re.sub(r'voiceDictationNotAvailable = "此设备不支持语音听写"\s*\)', zh_add, content)

# Japanese
ja_add = """    voiceDictationNotAvailable = "この端末では音声入力を使用できません",
    firstNameLabel = "名",
    lastNameLabel = "姓",
    emailLabel = "メールアドレス",
    phoneLabel = "電話番号",
    oldPasswordLabel = "現在のパスワード",
    newPasswordLabel = "新しいパスワード",
    confirmPasswordLabel = "パスワード再入力",
    passwordEmptyError = "パスワードを入力してください",
    passwordMismatchError = "パスワードが一致しません",
    passwordUpdatedSuccess = "パスワードを変更しました",
    profileUpdatedSuccess = "プロフィールを更新しました",
    aiWelcomeMessage = "こんにちは！AIアシスタントです。すべての通話に関する質問や、特定の連絡先について尋ねることができます。",
    aiNewConversationStarted = "新しい会話が開始されました！通話に関する質問を入力してください。"
)"""
content = re.sub(r'voiceDictationNotAvailable = "この端末では音声入力を使用できません"\s*\)', ja_add, content)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Updated AppStrings.kt successfully!")
