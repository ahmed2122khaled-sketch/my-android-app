# mr.x — إعداد رسائل استعادة كلمة المرور

## 1) اسم المرسل الظاهر
في Supabase Dashboard افتح Authentication → SMTP Settings / Email Provider، واضبط:

- Sender name: `mr.x`
- Sender email: بريد موثوق من نطاقك، مثل `no-reply@your-domain.example`

للاستخدام الإنتاجي، استخدم Custom SMTP. خدمة SMTP الافتراضية في Supabase محدودة ومخصصة للتجربة، وقد لا ترسل إلى عناوين عامة غير مصرح بها.

## 2) قالب Recovery
في Authentication → Email Templates → Reset password / Recovery:

- Subject: `mr.x — استعادة كلمة المرور`
- Body: استخدم محتوى `supabase-email/recovery.html`

يجب الإبقاء على `{{ .ConfirmationURL }}` داخل زر إعادة التعيين.

## 3) رابط العودة إلى التطبيق
أضف إلى Authentication → URL Configuration → Redirect URLs:

`mrx://auth/callback`

والتطبيق يستخدم هذا الرابط عند طلب الاستعادة.

## 4) ملاحظة مهمة
تعديل APK وحده لا يغيّر اسم From في البريد المرسل؛ اسم المرسل يتم تحديده من خادم Supabase/SMTP. التطبيق يعرض للمستخدم داخل الواجهة أن رسالة الاستعادة صادرة من mr.x، بينما البريد الفعلي سيظهر باسم mr.x بعد ضبط Sender name في Auth SMTP.
