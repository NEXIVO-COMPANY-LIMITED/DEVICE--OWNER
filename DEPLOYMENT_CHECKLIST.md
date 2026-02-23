# PAYO Deployment Checklist

## 🎯 QUICK START

Follow these phases in order. Check off each step as you complete it.

---

## 📍 PHASE 1: CREATE KEYSTORE ✅

### Windows Command Prompt Steps
- [ ] Press Windows Key + R
- [ ] Type: `cmd` and press Enter
- [ ] Type: `cd Desktop` and press Enter
- [ ] Type: `keytool` and press Enter (verify it works)
- [ ] Copy and paste this command:
  ```
  keytool -genkey -v -keystore payo-release.keystore -alias payo-release -keyalg RSA -keysize 2048 -validity 10000
  ```
- [ ] Answer all prompts (see guide for details)
- [ ] Verify file exists: `dir payo-release.keystore`
- [ ] Type: `exit` to close Command Prompt

### Save Keystore Info
- [ ] Create file: `PAYO_Keystore_Info.txt` on Desktop
- [ ] Include all certificate details
- [ ] Save password securely

### Backup (CRITICAL!)
- [ ] Email both files to yourself
- [ ] Copy to USB drive (if available)
- [ ] Upload to Google Drive

**Status: ✅ PHASE 1 COMPLETE**

---

## 📍 PHASE 2: SIGN YOUR APP ✅

### Prepare Project
- [ ] `keystore.properties` file exists in project root
- [ ] Contains correct passwords and paths

### Android Studio
- [ ] Open PAYO project
- [ ] Wait for Gradle sync
- [ ] Click `Build` → `Clean Project`
- [ ] Click `Build` → `Rebuild Project`
- [ ] Verify: `BUILD SUCCESSFUL` at bottom

### Generate Signed Bundle
- [ ] Click `Build` → `Generate Signed Bundle / APK...`
- [ ] Select: `Android App Bundle`
- [ ] Click `Next`
- [ ] Click `Choose existing...`
- [ ] Select: `payo-release.keystore` from Desktop
- [ ] Click `OK`
- [ ] Enter keystore password: `Payo2025#SecureKey`
- [ ] Select key alias: `payo-release`
- [ ] Enter key password: `Payo2025#SecureKey`
- [ ] Check: "Remember passwords"
- [ ] Click `Next`
- [ ] Verify: Build Variants = `release` (NOT debug)
- [ ] Check all signature versions (V1, V2, V3)
- [ ] Click `Finish`
- [ ] Wait for build (2-5 minutes)
- [ ] Verify: `BUILD SUCCESSFUL`

### Copy to Desktop
- [ ] Find: `app-release.aab` in `app\release\`
- [ ] Copy to Desktop
- [ ] Rename to: `payo-signed.aab`

### Verify Signature (Optional)
- [ ] Open Command Prompt
- [ ] Type: `cd Desktop`
- [ ] Type: `jarsigner -verify -verbose -certs payo-signed.aab`
- [ ] Verify: `jar verified.` message

**Status: ✅ PHASE 2 COMPLETE**

---

## 📍 PHASE 3: CREATE GRAPHICS ✅

### App Icon (512x512)
- [ ] Go to: https://www.canva.com
- [ ] Create design: 512x512
- [ ] Design icon with PAYO branding
- [ ] Download as PNG
- [ ] Rename to: `payo-icon-512.png`
- [ ] Move to Desktop

### Feature Graphic (1024x500)
- [ ] Create design: 1024x500
- [ ] Design banner with PAYO branding
- [ ] Download as PNG
- [ ] Rename to: `payo-feature-graphic.png`
- [ ] Move to Desktop

### Screenshots (at least 2)
- [ ] Take 2-4 screenshots of app
- [ ] Rename: `screenshot1.png`, `screenshot2.png`
- [ ] Move to Desktop

### Privacy Policy
- [ ] Go to: https://docs.google.com
- [ ] Create blank document
- [ ] Copy privacy policy template (see guide)
- [ ] Replace placeholders with your info
- [ ] Click `File` → `Share` → `Publish to web`
- [ ] Copy the URL
- [ ] Save URL to: `privacy-policy-url.txt` on Desktop

### Desktop Files Verification
- [ ] ✅ payo-signed.aab
- [ ] ✅ payo-icon-512.png
- [ ] ✅ payo-feature-graphic.png
- [ ] ✅ screenshot1.png
- [ ] ✅ screenshot2.png
- [ ] ✅ privacy-policy-url.txt

**Status: ✅ PHASE 3 COMPLETE**

---

## 📍 PHASE 4: UPLOAD TO GOOGLE PLAY CONSOLE ✅

### Create App
- [ ] Go to: https://play.google.com/console
- [ ] Sign in with Google account
- [ ] Click `Create app`
- [ ] App name: `PAYO`
- [ ] Default language: `English (United States)`
- [ ] App or game: `App`
- [ ] Free or paid: `Free`
- [ ] Check both declarations
- [ ] Click `Create app`

### Complete Setup Tasks
- [ ] Task 1: App Access → Set up → Save
- [ ] Task 2: Ads → Set up → Save
- [ ] Task 3: Content Rating → Complete questionnaire
- [ ] Task 4: Target Audience → Set up → Save
- [ ] Task 5: News App → Set up → Save
- [ ] Task 6: COVID-19 Apps → Set up → Save
- [ ] Task 7: Data Safety → Complete form
- [ ] Task 8: Government Apps → Set up → Save
- [ ] Task 9: Financial Features → Set up → Save
- [ ] Task 10: Privacy Policy → Paste URL → Save

### Store Listing
- [ ] Click `Grow` → `Store presence` → `Main store listing`
- [ ] App name: `PAYO`
- [ ] Short description: (see guide)
- [ ] Full description: (see guide)
- [ ] Upload app icon: `payo-icon-512.png`
- [ ] Upload feature graphic: `payo-feature-graphic.png`
- [ ] Upload screenshots: `screenshot1.png`, `screenshot2.png`
- [ ] Category: `Business`
- [ ] Contact email: your-email@example.com
- [ ] Contact phone: +255-XXX-XXX-XXX
- [ ] Click `Save`

### Upload App Bundle
- [ ] Click `Release` → `Testing` → `Internal testing`
- [ ] Click `Create new release`
- [ ] Click `Upload` under "App bundles"
- [ ] Select: `payo-signed.aab` from Desktop
- [ ] Wait for upload (2-5 minutes)
- [ ] Release name: `1.0.0`
- [ ] Release notes: (see guide)
- [ ] Click `Save`
- [ ] Click `Review release`
- [ ] Click `Start rollout to Internal testing`
- [ ] Click `Rollout`

### Add Testers
- [ ] Scroll to "Testers"
- [ ] Click `Create email list`
- [ ] List name: `PAYO Testers`
- [ ] Add your email(s)
- [ ] Click `Save changes`
- [ ] Copy testing link
- [ ] Save to: `testing-link.txt` on Desktop

**Status: ✅ PHASE 4 COMPLETE**

---

## 🎉 ALL PHASES COMPLETE!

### What Happens Next
- [ ] Google reviews your app (1-24 hours)
- [ ] You receive email with result
- [ ] Check Play Console for status

### If Approved ✅
- [ ] Status shows "Available"
- [ ] Download Google-signed APK
- [ ] Host on your server
- [ ] Update QR code
- [ ] Test on Pixel phone

### If Flagged ⚠️
- [ ] Read Google's feedback
- [ ] Address concerns
- [ ] Resubmit

---

## 📁 FILES CREATED

### In Project Root
- ✅ `keystore.properties` - Signing configuration
- ✅ `PLAY_STORE_DEPLOYMENT_GUIDE.md` - Full guide
- ✅ `DEPLOYMENT_CHECKLIST.md` - This file

### On Desktop
- ✅ `payo-release.keystore` - Your signing key
- ✅ `PAYO_Keystore_Info.txt` - Keystore details
- ✅ `payo-signed.aab` - Signed app bundle
- ✅ `payo-icon-512.png` - App icon
- ✅ `payo-feature-graphic.png` - Feature graphic
- ✅ `screenshot1.png` - App screenshot
- ✅ `screenshot2.png` - App screenshot
- ✅ `privacy-policy-url.txt` - Privacy policy URL
- ✅ `testing-link.txt` - Testing link

---

## ⚠️ IMPORTANT REMINDERS

1. **NEVER lose your keystore file or password**
   - You can NEVER update your app without it
   - Keep multiple backups

2. **Keep keystore.properties secure**
   - Don't commit to public repositories
   - Add to .gitignore

3. **Passwords are case-sensitive**
   - Use exactly: `Payo2025#SecureKey`

4. **Build must be "release" not "debug"**
   - Double-check in Android Studio

5. **App bundle must be .aab not .apk**
   - Google Play requires .aab format

---

## 🆘 QUICK TROUBLESHOOTING

| Problem | Solution |
|---------|----------|
| Keytool not found | Install Java from oracle.com |
| Build fails | Clean project, rebuild, check keystore.properties |
| Upload fails | Verify file is .aab, check file size |
| Google rejects app | Check Device Admin permissions, privacy policy |
| Can't find app-release.aab | Look in `app\release\` folder |

---

## 📞 NEXT STEPS

1. Follow this checklist step-by-step
2. Refer to `PLAY_STORE_DEPLOYMENT_GUIDE.md` for details
3. Keep all files on Desktop until upload is complete
4. Save the testing link for beta testers

Good luck! 🚀
